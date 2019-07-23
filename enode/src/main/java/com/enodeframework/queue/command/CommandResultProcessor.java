package com.enodeframework.queue.command;

import com.enodeframework.commanding.CommandResult;
import com.enodeframework.commanding.CommandReturnType;
import com.enodeframework.commanding.CommandStatus;
import com.enodeframework.commanding.ICommand;
import com.enodeframework.common.exception.ENodeRuntimeException;
import com.enodeframework.common.io.AsyncTaskResult;
import com.enodeframework.common.io.AsyncTaskStatus;
import com.enodeframework.common.scheduling.Worker;
import com.enodeframework.common.utilities.RemoteReply;
import com.enodeframework.queue.domainevent.DomainEventHandledMessage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author anruence@gmail.com
 */
public class CommandResultProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommandResultProcessor.class);

    private InetSocketAddress bindAddress;

    private NetServer netServer;

    private int port = 2019;

    private int completionSourceTimeout = 5000;

    private Cache<String, CommandTaskCompletionSource> commandTaskDict;

    private BlockingQueue<CommandResult> commandExecutedMessageLocalQueue;

    private BlockingQueue<DomainEventHandledMessage> domainEventHandledMessageLocalQueue;

    private Worker commandExecutedMessageWorker;

    private Worker domainEventHandledMessageWorker;

    private boolean started;

    public CommandResultProcessor() {
        Vertx vertx = Vertx.vertx();
        netServer = vertx.createNetServer();
        netServer.connectHandler(sock -> {
            sock.endHandler(v -> sock.close()).exceptionHandler(t -> {
                logger.error("Failed to start NetServer", t);
                sock.close();
            }).handler(buffer -> {
                RemoteReply name = buffer.toJsonObject().mapTo(RemoteReply.class);
                processRequestInternal(name);
            });
        });

        commandExecutedMessageLocalQueue = new LinkedBlockingQueue<>();
        domainEventHandledMessageLocalQueue = new LinkedBlockingQueue<>();
        commandExecutedMessageWorker = new Worker("ProcessExecutedCommandMessage", () -> {
            processExecutedCommandMessage(commandExecutedMessageLocalQueue.take());
        });
        domainEventHandledMessageWorker = new Worker("ProcessDomainEventHandledMessage", () -> {
            processDomainEventHandledMessage(domainEventHandledMessageLocalQueue.take());
        });
    }

    public void registerProcessingCommand(ICommand command, CommandReturnType commandReturnType, CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource) {
        if (commandTaskDict.asMap().containsKey(command.getId())) {
            throw new ENodeRuntimeException(String.format("Duplicate processing command registration, type:%s, id:%s", command.getClass().getName(), command.getId()));
        }
        commandTaskDict.asMap().put(command.getId(), new CommandTaskCompletionSource(command.getAggregateRootId(), commandReturnType, taskCompletionSource));
    }

    private void processTimeoutCommand(String commandId, CommandTaskCompletionSource commandTaskCompletionSource) {
        if (commandTaskCompletionSource != null) {
            CommandResult commandResult = new CommandResult(CommandStatus.Failed, commandId, commandTaskCompletionSource.getAggregateRootId(), "Wait command notify timeout.", String.class.getName());
            commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult));
        }
    }

    public void processFailedSendingCommand(ICommand command) {
        CommandTaskCompletionSource commandTaskCompletionSource = commandTaskDict.asMap().remove(command.getId());
        if (commandTaskCompletionSource != null) {
            CommandResult commandResult = new CommandResult(CommandStatus.Failed, command.getId(), command.getAggregateRootId(), "Failed to send the command.", String.class.getName());
            commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult));
        }
    }

    public CommandResultProcessor start() {
        if (started) {
            return this;
        }
        commandTaskDict = CacheBuilder.newBuilder()
                .expireAfterWrite(completionSourceTimeout, TimeUnit.MILLISECONDS)
                .removalListener((RemovalListener<String, CommandTaskCompletionSource>) notification -> {
                    if (notification.getCause().equals(RemovalCause.EXPIRED)) {
                        processTimeoutCommand(notification.getKey(), notification.getValue());
                    }
                }).build();
        bindAddress = new InetSocketAddress(port);
        netServer.listen(port);
        commandExecutedMessageWorker.start();
        domainEventHandledMessageWorker.start();
        started = true;
        logger.info("CommandResultProcessor started, bindAddress:{}", bindAddress);
        return this;
    }

    public CommandResultProcessor shutdown() {
        netServer.close();
        commandExecutedMessageWorker.stop();
        domainEventHandledMessageWorker.stop();
        return this;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public void processRequestInternal(RemoteReply reply) {
        if (reply.getCode() == CommandReturnType.CommandExecuted.getValue()) {
            CommandResult result = reply.getCommandResult();
            commandExecutedMessageLocalQueue.add(result);
        } else if (reply.getCode() == CommandReturnType.EventHandled.getValue()) {
            DomainEventHandledMessage message = reply.getEventHandledMessage();
            domainEventHandledMessageLocalQueue.add(message);
        } else {
            logger.error("Invalid remoting reply: {}", reply);
        }
    }

    private void processExecutedCommandMessage(CommandResult commandResult) {
        CommandTaskCompletionSource commandTaskCompletionSource = commandTaskDict.asMap().get(commandResult.getCommandId());
        if (commandTaskCompletionSource == null) {
            logger.error("CommandReturnResult failed, CommandTaskCompletionSource not found, maybe expired, commandResult:{}", commandResult);
            return;
        }
        if (commandTaskCompletionSource.getCommandReturnType().equals(CommandReturnType.CommandExecuted)) {
            commandTaskDict.asMap().remove(commandResult.getCommandId());

            if (commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult))) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Command result return, {}", commandResult);
                }
            }
        } else if (commandTaskCompletionSource.getCommandReturnType().equals(CommandReturnType.EventHandled)) {
            if (commandResult.getStatus().equals(CommandStatus.Failed) || commandResult.getStatus().equals(CommandStatus.NothingChanged)) {
                commandTaskDict.asMap().remove(commandResult.getCommandId());
                if (commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult))) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Command result return, {}", commandResult);
                    }
                }
            }
        }

    }

    private void processDomainEventHandledMessage(DomainEventHandledMessage message) {
        CommandTaskCompletionSource commandTaskCompletionSource = commandTaskDict.asMap().remove(message.getCommandId());
        if (commandTaskCompletionSource != null) {
            CommandResult commandResult = new CommandResult(CommandStatus.Success, message.getCommandId(), message.getAggregateRootId(), message.getCommandResult(), message.getCommandResult() != null ? String.class.getName() : null);

            if (commandTaskCompletionSource.getTaskCompletionSource().complete(new AsyncTaskResult<>(AsyncTaskStatus.Success, commandResult))) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Command result return, {}", commandResult);
                }
            }
        }
    }

    public void setPort(int port) {
        this.port = port;
    }

    public CommandResultProcessor setCompletionSourceTimeout(int completionSourceTimeout) {
        this.completionSourceTimeout = completionSourceTimeout;
        return this;
    }

    class CommandTaskCompletionSource {
        private String aggregateRootId;
        private CommandReturnType commandReturnType;
        private CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource;

        public CommandTaskCompletionSource(String aggregateRootId, CommandReturnType commandReturnType, CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource) {
            this.aggregateRootId = aggregateRootId;
            this.commandReturnType = commandReturnType;
            this.taskCompletionSource = taskCompletionSource;
        }

        public CommandReturnType getCommandReturnType() {
            return commandReturnType;
        }

        public void setCommandReturnType(CommandReturnType commandReturnType) {
            this.commandReturnType = commandReturnType;
        }

        public CompletableFuture<AsyncTaskResult<CommandResult>> getTaskCompletionSource() {
            return taskCompletionSource;
        }

        public void setTaskCompletionSource(CompletableFuture<AsyncTaskResult<CommandResult>> taskCompletionSource) {
            this.taskCompletionSource = taskCompletionSource;
        }

        public String getAggregateRootId() {
            return aggregateRootId;
        }

        public CommandTaskCompletionSource setAggregateRootId(String aggregateRootId) {
            this.aggregateRootId = aggregateRootId;
            return this;
        }
    }
}

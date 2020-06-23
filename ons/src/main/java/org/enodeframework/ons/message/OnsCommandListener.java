package org.enodeframework.ons.message;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.order.ConsumeOrderContext;
import com.aliyun.openservices.ons.api.order.MessageOrderListener;
import com.aliyun.openservices.ons.api.order.OrderAction;
import org.enodeframework.common.io.Task;
import org.enodeframework.queue.IMessageHandler;
import org.enodeframework.queue.QueueMessage;

import java.util.concurrent.CountDownLatch;

/**
 * @author anruence@gmail.com
 */
public class OnsCommandListener implements MessageOrderListener {

    private final IMessageHandler commandListener;

    public OnsCommandListener(IMessageHandler commandListener) {
        this.commandListener = commandListener;
    }

    @Override
    public OrderAction consume(Message message, ConsumeOrderContext context) {
        final CountDownLatch latch = new CountDownLatch(1);
        QueueMessage queueMessage = OnsTool.covertToQueueMessage(message);
        commandListener.handle(queueMessage, m -> {
            latch.countDown();
        });
        Task.await(latch);
        return OrderAction.Success;
    }
}

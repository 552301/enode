package org.enodeframework.tests.EventHandlers;

import org.enodeframework.annotation.Event;
import org.enodeframework.annotation.Priority;
import org.enodeframework.annotation.Subscribe;
import org.enodeframework.common.io.AsyncTaskResult;
import org.enodeframework.tests.Domain.Event1;
import org.enodeframework.tests.Domain.Event2;
import org.enodeframework.tests.TestClasses.CommandAndEventServiceTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@Priority(2)
@Event
public class Handler122 {
    private Logger _logger = LoggerFactory.getLogger(Handler122.class);

    @Subscribe
    public AsyncTaskResult HandleAsync(Event1 evnt, Event2 evnt2) {
        _logger.info("event1,event2 handled by handler2.");
        CommandAndEventServiceTest.HandlerTypes.computeIfAbsent(2, k -> new ArrayList<>()).add(getClass().getName());
        return AsyncTaskResult.Success;
    }
}
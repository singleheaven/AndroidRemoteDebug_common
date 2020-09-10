package com.freddy.chat.im.handler;

import com.freddy.chat.bean.AppMessage;
import com.freddy.chat.event.CEventCenter;
import com.freddy.chat.event.Events;
import jdi.log.Log;

public class GoodbyeMessageHandler implements IMessageHandler {
    private static final String TAG = GoodbyeMessageHandler.class.getSimpleName();

    @Override
    public void execute(AppMessage message) {
        Log.d(TAG, "收到断开消息，message=" + message);
        CEventCenter.dispatchEvent(Events.GOODBYE_MESSAGE, 0, 0, message);
    }
}

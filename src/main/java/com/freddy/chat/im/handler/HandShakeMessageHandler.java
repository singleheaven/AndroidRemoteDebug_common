package com.freddy.chat.im.handler;

import jdi.log.Log;

import com.freddy.chat.bean.AppMessage;
import com.freddy.chat.event.CEventCenter;
import com.freddy.chat.event.Events;

public class HandShakeMessageHandler implements IMessageHandler {
    private static final String TAG = HandShakeMessageHandler.class.getSimpleName();

    @Override
    public void execute(AppMessage message) {
        Log.d(TAG, "收到握手消息，message=" + message);
        CEventCenter.dispatchEvent(Events.HANDSHAKE_MESSAGE, 0, 0, message);
    }
}

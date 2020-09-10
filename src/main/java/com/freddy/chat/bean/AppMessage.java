package com.freddy.chat.bean;

import java.util.Arrays;

/**
 * <p>@ProjectName:     NettyChat</p>
 * <p>@ClassName:       AppMessage.java</p>
 * <p>@PackageName:     com.freddy.chat.bean</p>
 * <b>
 * <p>@Description:     App消息，用于把protobuf消息转换成app可用的消息类型</p>
 * </b>
 * <p>@author:          FreddyChen</p>
 * <p>@date:            2019/04/10 00:01</p>
 * <p>@email:           chenshichao@outlook.com</p>
 */
public class AppMessage {

    private Head head;  // 消息头
    private byte[] body;// 消息体

    public Head getHead() {
        return head;
    }

    public void setHead(Head head) {
        this.head = head;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "AppMessage{" +
                "head=" + head +
                ", body='" + Arrays.toString(body) + '\'' +
                '}';
    }
}

package com.freddy.chat.bean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.freddy.chat.im.MessageType;
import com.freddy.chat.utils.StringUtil;

/**
 * <p>@ProjectName:     NettyChat</p>
 * <p>@ClassName:       SingleMessage.java</p>
 * <p>@PackageName:     com.freddy.chat.bean</p>
 * <b>
 * <p>@Description:     单聊消息</p>
 * </b>
 * <p>@author:          FreddyChen</p>
 * <p>@date:            2019/04/10 03:24</p>
 * <p>@email:           chenshichao@outlook.com</p>
 */
public class SingleMessage extends ContentMessage implements Cloneable {
    public SingleMessage() {
        setMsgType(MessageType.SINGLE_CHAT.getMsgType());
        setMsgContentType(MessageType.MessageContentType.TEXT.getMsgContentType());
        setTimestamp(System.currentTimeMillis());
    }

    @Override
    public int hashCode() {
        try {
            return this.msgId.hashCode();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof SingleMessage)) {
            return false;
        }

        return StringUtil.equals(this.msgId, ((SingleMessage) obj).getMsgId());
    }

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}

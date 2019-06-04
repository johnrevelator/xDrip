package com.eveningoutpost.dexdrip.insulin.watlaa.messages;

import com.google.gson.annotations.Expose;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import lombok.Getter;

import static com.eveningoutpost.dexdrip.insulin.inpen.Constants.COUNTER_START;

// jamorham

public class UnitTx extends BaseTx {

    public UnitTx(final byte[] param) {
        init(1);
        final ByteBuffer byteBuffer = ByteBuffer.wrap(param);
        data.putInt(byteBuffer.getInt());
    }


}

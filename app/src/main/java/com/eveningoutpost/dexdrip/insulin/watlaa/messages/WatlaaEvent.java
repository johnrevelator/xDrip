package com.eveningoutpost.dexdrip.insulin.watlaa.messages;

public class WatlaaEvent {
    public static final String CALLIBRATION = "CALLIBRATION";
    public static final String UNITS = "UNITS";

    private String type;
    private String value;

    public WatlaaEvent(String type,String value){
        this.type=type;
        this.value=value;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}

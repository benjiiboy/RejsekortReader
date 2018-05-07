package com.example.benjamin_pc.rejsekortreader;

/**
 * Created by Benjamin-pc on 07-05-2018.
 */

public class NFCObject {
    public enum NFCType {
        AID,
        Amount,
        CurrencyUnit,
        Date,
        Long,
        Status,
        TCDIAID
    }

    private final NFCType type;
    private final long value;
    public NFCObject next;

    public NFCObject(long l, NFCType t, NFCObject o) {
        value = l;
        type = t;
        next = o;
    }

    public NFCObject(long l, NFCType t) {
        value = l;
        type = t;
        next = null;
    }

    public NFCObject(long l, NFCObject o) {
        value = l;
        type = NFCType.Long;
        next = o;
    }

    public NFCObject(long l) {
        value = l;
        type = NFCType.Long;
        next = null;
    }

    public NFCType getType() { return type; }
    public long getValue() { return value; }
    public boolean hasMore() { return (null != next); }
    public NFCObject getNext() { return next; }

}

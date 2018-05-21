package com.example.benjamin_pc.rejsekortreader;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Benjamin-pc on 07-05-2018.
 */

public class NFCCard {
    public byte[] bytes;
    private int unit = 1;
    public String currency = "Not set";
    private int[] dynamicData = null;
    private int[] tcdi = null;
    /** These maps contains the RKFObjects or values of the card with the
     value name (as taken from the specifications, if it exist) for key.
     If multiple values have the same name, the RKFObjects will form a
     single linked list */
    public Map<String,NFCObject> firstSector = null;
    public Map<String,NFCObject> directory = null;
    public Map<String,NFCObject> purse = null;
    public Map<String,NFCObject> dynPurse = null;

    private int pos;
    private int cardVersion = -1;
    private String debugString = "";
    private boolean isRejseKort = false;

    public NFCCard() {
        bytes = new byte[16*48];
    }

    public NFCCard(byte[] b) {
        bytes = b;
        parseCard();
    }

    public void addBlock(int sector, int block, byte[] b) {
        if((sector > 15) || (block > 2)) // skip keyblocks and sectors above 16
            return;
        int bytePos = (sector*48) + (block*16);
        System.arraycopy(b, 0, bytes, bytePos, 16);
    }

    public boolean parseCard() {
        if(bytes == null) {
            return false;
        }
        pos = 0;

        parseFirstSector();
        if (isRejseKort)
        {
            parseTCDI(128*3);
        }

        for(int i=2;i<(bytes.length/48);i++) {
            if(i < 16 && tcdi[i] == 0x01)
                continue;
            parseSector(i);
        }

        return true;
    }

    private void parseSector(int n) {
        int sb = n*48;
        int ident;
        pos = sb*8;
        ident = getIntFromPos(8);

        switch (ident) {
            case 0x85:
                parsePurse(sb*8);
                break;
        }
    }

    private void parseFirstSector() {
        long ret;
        firstSector = new HashMap<String,NFCObject>();
        pos = 0;
        addAttribute("Serial number", 32, firstSector);

        // go to next block and skip MAD Info Byte
        pos = 16*9;
        //addAttribute("MAD Info Byte", 16, firstSector);
        cardVersion = (int)addAttribute("Card version", 6, firstSector);

        ret = addAttribute("Card provider", 12, firstSector, NFCObject.NFCType.AID);
        if(0x7d0 == ret) {
            isRejseKort = true;
        }

        addAttribute("Card validity end date", 14, firstSector,NFCObject.NFCType.Date);
        addAttribute("Status", 8, firstSector, NFCObject.NFCType.Status);
        ret = addAttribute("Currency unit", 16, firstSector, NFCObject.NFCType.CurrencyUnit);
        setCurrencyUnit((int)ret);
        addAttribute("Event log version", 6, firstSector);
    }

    // Directory
    private void parseTCDI(int n) {
        directory = new HashMap<String,NFCObject>();
        tcdi = new int[16];

        pos = n;
        int s = 1;
        boolean aid_bool = true;
        for(int i=1;i<31;i++) {
            if(aid_bool) {
                addAttribute("AID (Sector"+s+")", 12, directory, NFCObject.NFCType.TCDIAID);
            }
            else {
                tcdi[s] = (int)addAttribute("PIX (Sector"+s+")", 12, directory);
                s++;
            }
            if(i%10 == 0) // skip MAC at end of block
                pos+=8;
            aid_bool = !aid_bool;
        }

    }

    // TCPU: Purse 0x85
    private void parsePurse(int n) {
        if(null != purse)  // skip any extra purse found (ugly fix for rejsekort with backup purse)
            return;
        purse = new HashMap<String,NFCObject>();
        dynPurse = new HashMap<String,NFCObject>();

        pos = n + 8;

        int version = (int)addAttribute("Version", 6, purse);
        addAttribute("AID", 12, purse, NFCObject.NFCType.AID);

        if(version < 6) {
            addAttribute("Serial Number", 32, purse);
            addAttribute("Start Date", 14, purse, NFCObject.NFCType.Date);
        }

        int dynField = 1;
        int blockLen = 128;
        if(isRejseKort) {
            blockLen = 256;
        }

        if(null != dynamicData) {
            dynField = dynamicData[n/(48*8)];
        }
        else {
            pos = n+blockLen;
            // determine latest by checking the higher TX number
            int txnr1 = getIntFromPos(16);
            pos += blockLen;
            int txnr2 = getIntFromPos(16);
            if(txnr2 > txnr1) {
                dynField = 2;
            }
        }
        pos = n+blockLen;
        // skip to dynamic data
        if(dynField == 2) {
            // Latest data in second field
            pos = n+(blockLen*2);
            parseDynPurse(dynPurse, version);
        }
        else {
            parseDynPurse(dynPurse, version);
            pos = n+(blockLen*2);
        }
    }

    private void parseDynPurse(final Map<String,NFCObject> m, int version) {
        addAttribute("Transaction Number", 16, m);
        if(version < 6) {
            addAttribute("Purse Expiry Date", 14, m, NFCObject.NFCType.Date);
        }
        addAttribute("Value", 24, m, NFCObject.NFCType.Amount);
        if(version == 2) {
            addAttribute("Status", 8, m, NFCObject.NFCType.Status);
            addAttribute("Deposit", 20, m, NFCObject.NFCType.Amount);
        }
    }

    private long addAttribute(final String name, int length,
                              final Map<String,NFCObject> m) {
        long l = getLongFromPos(length);

        if(m.containsKey(name)) {
            m.put(name, new NFCObject(l, m.get(name)));
        }
        else {
            m.put(name, new NFCObject(l));
        }
        pos += length;
        debug(name+": "+l+" (0x"+Long.toHexString(l)+")");
        return l;
    }

    private long skipPos(int length) {
        long l = getLongFromPos(length);
        debug("Skipping "+length+" bits with value "+l+" (0x"+Long.toHexString(l)+")");
        pos += length;
        return l;
    }

    private long addAttribute(final String name, int length,
                              final Map<String,NFCObject> m, NFCObject.NFCType type) {
        Calendar d;
        long l = getLongFromPos(length);

        switch(type) {
            case AID:
                debug(name+": "+getVendor((int)l)+" (0x"+Long.toHexString(l)+")");
                break;
            case Amount:
                // MoneyAmount can be either of length 20 or 24 where the latter
                // can also be negative.
                // This will fix negative values if length is 24.
                if((24 == length) && (0x800000 == (l & 0x800000))) {
                    l = (l ^ 0xFFFFFFFFFF000000L);
                }
                debug(name+": "+getAmount((int)l)+" (0x"+Long.toHexString(l & 0x0000000000FFFFFFL)+")");
                break;
            case CurrencyUnit:
                debug(name+": "+ getCurrency((int)l) + " " + getUnit((int)l)+" (0x"+Long.toHexString(l)+")");
                break;
        }

        if(m.containsKey(name)) {
            m.put(name, new NFCObject(l, type, m.get(name)));
        }
        else {
            m.put(name, new NFCObject(l, type));
        }
        pos += length;

        return l;
    }


    private void setCurrencyUnit(int i) {
        switch (i & 0xF000) {
            case 0x0000:
                unit = 1;
                break;
            case 0x1000:
                unit = 10;
                break;
            case 0x2000:
                unit = 100;
                break;
            case 0x9000:
                unit = 2;
                break;
            default:
                unit = 1;
        }

        currency = getCurrency(i);
    }

    public static String getCurrency(int i) {
        // ISO 4217 currency codes in hexadecimal
        switch (i & 0xFFF) {
            case 0x208:
                return "DKK";
            default:
                return "Unknown";
        }
    }

    public static String getUnit(int i) {
        switch (i & 0xF000) {
            case 0x0000:
                return "Main unit";
            case 0x1000:
                return "Minor unit, 1/10 of main unit";
            case 0x2000:
                return "Minor unit, 1/100 of main unit";
            case 0x9000:
                return "Minor unit, 1/2 of main unit";
            default:
                return "Unknown unit";
        }
    }



    private int getIntFromPos(int length) {
        return (int)getLongFromPos(length);
    }

    private long getLongFromPos(int length) {
        int sByte, eByte, sBit, eBits, mask;
        long r;
        r = 0;
        sByte = pos/8;          // get start byte
        eByte = (pos+length)/8; // get end byte


        sBit = pos % 8;             // Find what bit to start on
        eBits = (pos + length) % 8; // number of bits to read for last byte


        // process first byte
        mask = (0xFF << sBit) & 0xFF;
        if(sByte == eByte) {
            mask = (0xFF >> 8-eBits) & mask;
            return (bytes[sByte] & mask) >> sBit;
        }
        r = (bytes[sByte] & mask) >> sBit;


        // build up long from whole bytes
        int bPos = 1;
        for(int i = sByte+1; i < eByte; i++) {
            r = ((long)(bytes[i] & 0xFF) << ((bPos*8)-sBit)) | r;
            bPos++;
        }

        // add last bits
        if(0 == eBits)    // if there is zero bits left to read we are done
            return r;
        else
            mask = (0xFF >> (8-eBits));
        r = ((long)(bytes[eByte] & mask) << ((bPos*8)-sBit)) | r;

        return r;
    }

    public String getAmount(int i) {
        double d = ((double)i/unit);
        return new DecimalFormat("0.00").format(d) + " " + currency;
    }


    public static String getTCDIAID(int i) {
        switch(i) {
            case 0x00:
                return "Sector free";
            case 0x01:
                return "Sector defective";
            case 0x02:
                return "Sector reserved";
            case 0x05:
                return "Application Status (TCAS)";
            case 0x06:
                return "Directory (TCDI)";
            case 0x0A:
                return "Event Log (TCEL)";
            case 0x0B:
                return "Purse (TCPU)";
            case 0x0C:
                return "PTA-specific area for Purse (TCPU)";
            default:
                return getVendor(i);
        }
    }

    public static String getVendor(int i) {
        switch (i) {
            case 0x7D0:
                return "Rejsekort A/S";
            case 0x7D2:
                return "DSB";
            default:
                return "Unknown";
        }
    }

    private String posToHuman(int i) {
        int s = (i/(48*8));
        int b = ((i%(48*8))/128);
        int bp= ((i%(48*8))%128);
        return "Pos: "+i+", Sector: "+s+", Block: "+b+", Bit position: "+bp;
    }


    private void debug(final String s) {
        debugString = debugString + System.getProperty("line.separator") + s;
    }

    public String getDebug() { return debugString; }

}

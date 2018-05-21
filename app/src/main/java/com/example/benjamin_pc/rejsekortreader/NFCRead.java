package com.example.benjamin_pc.rejsekortreader;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.nfc.TagLostException;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * Created by Benjamin-pc on 07-05-2018.
 */

public class NFCRead extends Activity {
    private MifareClassic mfc = null;
    private Resources res;
    private TextView InfoTextView;
    private TextView AmountTextView;



    private Intent oldIntent = null;

    private String mainString;
    private String topString;

    public static enum CardType {
        REJSEKORT,
        UNINITIALIZED,
        UNKNOWN
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        res = getResources();
        InfoTextView = (TextView) findViewById(R.id.InfoTextView);
        AmountTextView = (TextView) findViewById(R.id.AmountTextView);

    }

    @Override
    public void onResume() {
        super.onResume();

        if(oldIntent != getIntent()) {
            if(NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction())) {
                Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
                mfc = null;
                if(tag != null) {
                    mfc = MifareClassic.get(tag);
                }
                if(null != mfc) {
                    readCard();
                }
            }
        }

        oldIntent = getIntent();
    }

    @Override
    public void onPause() {
        super.onPause();
    }


    private void readCard() {
        new ReadCardTask().execute();
    }

    private class ReadCardTask extends AsyncTask<Void, Void, Void> {
        private boolean tagLost = false;
        private CardType cardType = CardType.UNINITIALIZED;
        private NFCCard card = null;



        @Override
        protected Void doInBackground(Void... arg) {
            try {
                int bytePos = 0;
                mfc.connect();
                card = new NFCCard();

                if(!detectCardType()) {
                    card = null;
                    return null;
                }
                // only read the first 16 sectors
                for (int sector = 0; (sector < mfc.getSectorCount()) && (sector < 16); sector++) {
                    if(tryUnlock(sector)) {
                        int startBlock = mfc.sectorToBlock(sector);
                        for (int block = startBlock; block < (startBlock + 3); block++) {
                            card.addBlock(sector, (block%4), mfc.readBlock(block));
                        }
                    }
                }
            }
            catch (TagLostException e) {
                tagLost = true;
                card = null;
                return null;
            }
            catch (IOException e) {
                card = null;
            }
            finally {
                try {
                    mfc.close();
                }
                catch (IOException e) {
                    card = null;
                }
            }
            return null;
        }

        protected boolean detectCardType() throws IOException {
            if(mfc.authenticateSectorWithKeyA(6, hexStringToByteArray("fc00018778f7"))) {
                cardType = CardType.REJSEKORT;
            }
            else {
                cardType = CardType.UNKNOWN;
                return false;
            }
            return true;
        }

        protected boolean tryUnlock(int sector) throws IOException {
            boolean ret = false;

            switch(cardType) {
                case REJSEKORT:
                    switch(sector) {
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                        case 39:
                            ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("fc00018778f7"));
                            break;
                        case 8:
                        case 9:
                        case 10:
                        case 11:
                        case 12:
                            ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("0297927c0f77"));
                            break;
                        default:
                            ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("722bfcc5375f"));
                            break;
                    }
            }
            return ret;
        }



        @Override
        protected void onPostExecute(Void result) {
            if(tagLost) {
                // handle lost tag while reading
                card = null;
                tagLost = false;
            }
            else {
                // if the tag was not lost during read
                if(card != null) {
                        card.parseCard();

                        // get vendor and serial number and set the top string of main view
                        if(card.firstSector != null) {
                            topString = String.format(res.getString(R.string.top_string),
                                    card.firstSector.get("Serial number").getValue(),
                                    NFCCard.getVendor((int)card.firstSector.get("Card provider").getValue()));
                            InfoTextView.setText(topString);
                        }
                        // get the purse value and set it as main string of main view
                        if(card.dynPurse != null) {
                            mainString = card.getAmount((int)card.dynPurse.get("Value").getValue());

                            AmountTextView.setText(mainString);

                            double hentpung = (card.dynPurse.get("Value").getValue()/100);

                            if (hentpung < 50){
                                Notification simpleNotification = new Notification.Builder(NFCRead.this)
                                        .setSmallIcon(R.drawable.nfcicon)
                                        .setContentTitle("Rejsekortreader - lav saldo")
                                        .setContentText("Din saldo er: " + mainString + " Husk at tanke op!")
                                        .setDefaults(Notification.DEFAULT_ALL)
                                        .build();

                                NotificationManager notificationManager = (NotificationManager)
                                        getSystemService(NOTIFICATION_SERVICE);
                                notificationManager.notify(0, simpleNotification);

                            }
                        }
                }
                else if(CardType.UNKNOWN == cardType) {
                    InfoTextView.setText(R.string.unknown_card);
                }
            }
        }
    }

    private static byte[] hexStringToByteArray(final String s) {
        int len = s.length();
        byte[] b = new byte[(len/2)];
        for(int i=0;i<len;i+=2) {
            b[(i/2)] = (byte)((Character.digit(s.charAt(i), 16) << 4) +
                    Character.digit(s.charAt(i+1), 16));
        }
        return b;
    }

}

/* 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

//
// androidVNC is the Activity for setting VNC server IP and port.
//

package com.hyperdroidclient.androidVNC;

import android.app.Activity;
import android.app.ActivityManager.MemoryInfo;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.hyperdroidclient.R;
import com.hyperdroidclient.data.local.SharedPreferenceManager;
import com.hyperdroidclient.data.local.remote.VirtualMachine;
import com.hyperdroidclient.security.Decryption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class androidVNC extends Activity {
    private EditText ipText;
    private EditText portText;
    private EditText passwordText;
    private Button goButton;
    private TextView repeaterText;
    private RadioGroup groupForceFullScreen;
    private Spinner colorSpinner;
    private Spinner spinnerConnection;
    private VncDatabase database;
    private ConnectionBean selected;
    private EditText textNickname;
    private EditText textUsername;
    private CheckBox checkboxKeepPassword;
    private CheckBox checkboxLocalCursor;
    private boolean repeaterTextSet;
    private boolean fromIntent = false;
    public ArrayList<VirtualMachine> virtualMachines;

    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setContentView(R.layout.main);
        ipText = (EditText) findViewById(R.id.textIP);
        portText = (EditText) findViewById(R.id.textPORT);
        passwordText = (EditText) findViewById(R.id.textPASSWORD);
        textNickname = (EditText) findViewById(R.id.textNickname);
        textUsername = (EditText) findViewById(R.id.textUsername);
        goButton = (Button) findViewById(R.id.buttonGO);
        setSelectedFromView();
        findViewById(R.id.buttonRepeater).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                showDialog(R.layout.repeater_dialog);
            }
        });
        findViewById(R.id.buttonImportExport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(R.layout.importexport);
            }
        });
        colorSpinner = (Spinner) findViewById(R.id.colorformat);
        COLORMODEL[] models = COLORMODEL.values();
        ArrayAdapter<COLORMODEL> colorSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, models);
        groupForceFullScreen = (RadioGroup) findViewById(R.id.groupForceFullScreen);
        checkboxKeepPassword = (CheckBox) findViewById(R.id.checkboxKeepPassword);
        checkboxLocalCursor = (CheckBox) findViewById(R.id.checkboxUseLocalCursor);
        colorSpinner.setAdapter(colorSpinnerAdapter);
        colorSpinner.setSelection(0);
        spinnerConnection = (Spinner) findViewById(R.id.spinnerConnection);
        spinnerConnection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> ad, View view, int itemIndex, long id) {
                selected = (ConnectionBean) ad.getSelectedItem();
                updateViewFromSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> ad) {
                selected = null;
            }
        });
        spinnerConnection.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            /* (non-Javadoc)
             * @see android.widget.AdapterView.OnItemLongClickListener#onItemLongClick(android.widget.AdapterView, android.view.View, int, long)
             */
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {
                spinnerConnection.setSelection(arg2);
                selected = (ConnectionBean) spinnerConnection.getItemAtPosition(arg2);
                canvasStart();
                return true;
            }

        });
        repeaterText = (TextView) findViewById(R.id.textRepeaterId);
        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                canvasStart();
            }
        });

        database = new VncDatabase(this);
        // Log.d("EXTRA_CONN_DATA: ", actionID.toString());
        arriveOnPage();

    }

    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }



    /* (non-Javadoc)
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == R.layout.importexport)
            return new ImportExportDialog(this);
        else
            return new RepeaterDialog(this);
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.androidvncmenu, menu);
        return true;
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onMenuOpened(int, android.view.Menu)
     */
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        menu.findItem(R.id.itemDeleteConnection).setEnabled(selected != null && !selected.isNew());
        menu.findItem(R.id.itemSaveAsCopy).setEnabled(selected != null && !selected.isNew());
        return true;
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.itemSaveAsCopy:
                if (selected.getNickname().equals(textNickname.getText().toString()))
                    textNickname.setText("Copy of " + selected.getNickname());
                updateSelectedFromView();
                selected.set_Id(0);
                saveAndWriteRecent();
                arriveOnPage();
                break;
            case R.id.itemDeleteConnection:
                Utils.showYesNoPrompt(this, "Delete?", "Delete " + selected.getNickname() + "?",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                selected.Gen_delete(database.getWritableDatabase());
                                arriveOnPage();
                            }
                        }, null);
                break;
            case R.id.itemOpenDoc:
                Utils.showDocumentation(this);
                break;
        }
        return true;
    }

    private void updateViewFromSelected() {
        if (selected == null)
            return;
        ipText.setText(selected.getAddress());
        portText.setText(Integer.toString(selected.getPort()));
        if (selected.getKeepPassword() || selected.getPassword().length() > 0) {
            passwordText.setText(selected.getPassword());
        }
        groupForceFullScreen.check(selected.getForceFull() == BitmapImplHint.AUTO ? R.id.radioForceFullScreenAuto : (selected.getForceFull() == BitmapImplHint.FULL ? R.id.radioForceFullScreenOn : R.id.radioForceFullScreenOff));
        checkboxKeepPassword.setChecked(selected.getKeepPassword());
        checkboxLocalCursor.setChecked(selected.getUseLocalCursor());
        textNickname.setText(selected.getNickname());
        textUsername.setText(selected.getUserName());
        COLORMODEL cm = COLORMODEL.valueOf(selected.getColorModel());
        COLORMODEL[] colors = COLORMODEL.values();
        for (int i = 0; i < colors.length; ++i) {
            if (colors[i] == cm) {
                colorSpinner.setSelection(i);
                break;
            }
        }
        updateRepeaterInfo(selected.getUseRepeater(), selected.getRepeaterId());
    }

    /**
     * Called when changing view to match selected connection or from
     * Repeater dialog to update the repeater information shown.
     *
     * @param repeaterId If null or empty, show text for not using repeater
     */
    void updateRepeaterInfo(boolean useRepeater, String repeaterId) {
        if (useRepeater) {
            repeaterText.setText(repeaterId);
            repeaterTextSet = true;
        } else {
            repeaterText.setText(getText(R.string.repeater_empty_text));
            repeaterTextSet = false;
        }
    }

    //TODO Adding ip address dynamically
    //TODO Decrypting Cipher & getting Hash
    private void setSelectedFromView() {
        FirebaseDatabase database;
        final DatabaseReference mDatabase;
        database = FirebaseDatabase.getInstance();
        mDatabase = database.getReference();
        Query query = mDatabase.child("VirtualMachine").orderByKey();
        virtualMachines = new ArrayList<>();

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean exhaust = false;
                for (DataSnapshot postData : dataSnapshot.getChildren()) {
                    Log.d("Key :", postData.getKey());
                    VirtualMachine virtualMachine = postData.getValue(VirtualMachine.class);
                    assert virtualMachine != null;
                    Log.d("VM Address", virtualMachine.getAddress());
//                    String refInterval = virtualMachine.getRefInterval();
//                    String timeStamp = virtualMachine.getTimeStamp();
//                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
//                    String currentDateandTime = sdf.format(new Date());
//
                    //if (virtualMachine.getUUID().isEmpty() || virtualMachine.getUUID().equals(new SharedPreferenceManager(getApplicationContext()).getAccessToken())) {
                        //TODO Assign VM
                        ipText.setText(virtualMachine.getAddress());
                        portText.setText(virtualMachine.getPort());
                        String password = "";
                        try {
                            password = Decryption.decryptPassword(virtualMachine.getHash());
                            Log.d("Password",password);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        passwordText.setText(password);
                        new SharedPreferenceManager(getApplicationContext()).saveVMKey(postData.getKey());
                        mDatabase.child("VirtualMachine").child(postData.getKey()).child("UUID").setValue(new SharedPreferenceManager(getApplicationContext()).getAccessToken());
                        mDatabase.child("Users").child(new SharedPreferenceManager(getApplicationContext()).getAccessToken()).child("vmid").setValue(postData.getKey());
                        canvasStart();
                        break;
                    //}
                }
                ///if (exhaust) ;
                //TODO Please Wait
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("The read failed: ", databaseError.getMessage());
            }
        });


    }

    private void updateSelectedFromView() {

        if (selected == null) {
            return;
        }
        selected.setAddress(ipText.getText().toString());
        try {
            selected.setPort(Integer.parseInt(portText.getText().toString()));
        } catch (NumberFormatException nfe) {
            Log.d("NumberFormatException", nfe.toString());
        }
        selected.setNickname(textNickname.getText().toString());
        selected.setUserName(textUsername.getText().toString());
        selected.setForceFull(groupForceFullScreen.getCheckedRadioButtonId() == R.id.radioForceFullScreenAuto ? BitmapImplHint.AUTO : (groupForceFullScreen.getCheckedRadioButtonId() == R.id.radioForceFullScreenOn ? BitmapImplHint.FULL : BitmapImplHint.TILE));
        selected.setPassword(passwordText.getText().toString());
        selected.setKeepPassword(checkboxKeepPassword.isChecked());
        selected.setUseLocalCursor(checkboxLocalCursor.isChecked());
        selected.setColorModel(((COLORMODEL) colorSpinner.getSelectedItem()).nameString());
        if (repeaterTextSet) {
            selected.setRepeaterId(repeaterText.getText().toString());
            selected.setUseRepeater(true);
        } else {
            selected.setUseRepeater(false);
        }
    }

    protected void onStart() {
        super.onStart();
        arriveOnPage();
    }

    /**
     * Return the object representing the app global state in the database, or null
     * if the object hasn't been set up yet
     *
     * @param db App's database -- only needs to be readable
     * @return Object representing the single persistent instance of MostRecentBean, which
     * is the app's global state
     */
    static MostRecentBean getMostRecent(SQLiteDatabase db) {
        ArrayList<MostRecentBean> recents = new ArrayList<>(1);
        MostRecentBean.getAll(db, MostRecentBean.GEN_TABLE_NAME, recents, MostRecentBean.GEN_NEW);
        if (recents.size() == 0)
            return null;
        return recents.get(0);
    }

    void arriveOnPage() {
        ArrayList<ConnectionBean> connections = new ArrayList<>();
        ConnectionBean.getAll(database.getReadableDatabase(), ConnectionBean.GEN_TABLE_NAME, connections, ConnectionBean.newInstance);
        Collections.sort(connections);
        connections.add(0, new ConnectionBean());
        int connectionIndex = 0;
        if (connections.size() > 1) {
            MostRecentBean mostRecent = getMostRecent(database.getReadableDatabase());
            if (mostRecent != null) {
                for (int i = 1; i < connections.size(); ++i) {
                    if (connections.get(i).get_Id() == mostRecent.getConnectionId()) {
                        connectionIndex = i;
                        break;
                    }
                }
            }
        }
        spinnerConnection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                connections.toArray(new ConnectionBean[connections.size()])));
        spinnerConnection.setSelection(connectionIndex, false);
        selected = connections.get(connectionIndex);
        updateViewFromSelected();
        IntroTextDialog.showIntroTextIfNecessary(this, database);
    }

    protected void onStop() {
        super.onStop();
        if (selected == null) {
            return;
        }
        updateSelectedFromView();
        selected.save(database.getWritableDatabase());
    }

    VncDatabase getDatabaseHelper() {
        return database;
    }

    private void canvasStart() {
        // Log.d("canvasStart()",Utils.getMemoryInfo(this).toString());
        if (selected == null) return;
        MemoryInfo info = Utils.getMemoryInfo(this);
        if (info.lowMemory) {
            // Low Memory situation.  Prompt.
            Utils.showYesNoPrompt(this, "Continue?", "Android reports low system memory.\nContinue with VNC connection?", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    vnc();
                }
            }, null);
        } else
            vnc();
    }

    private void saveAndWriteRecent() {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            selected.save(db);
            MostRecentBean mostRecent = getMostRecent(db);
            if (mostRecent == null) {
                mostRecent = new MostRecentBean();
                mostRecent.setConnectionId(selected.get_Id());
                mostRecent.Gen_insert(db);
            } else {
                mostRecent.setConnectionId(selected.get_Id());
                mostRecent.Gen_update(db);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void vnc() {
        updateSelectedFromView();
        saveAndWriteRecent();
        Intent intent = new Intent(this, VncCanvasActivity.class);
        Log.d("VncConstants", selected.Gen_getValues().toString());
        intent.putExtra(VncConstants.CONNECTION, selected.Gen_getValues());
        startActivity(intent);
        if (fromIntent) {
            finish();
        }
    }
}

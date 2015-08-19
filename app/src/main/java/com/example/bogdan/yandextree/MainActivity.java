package com.example.bogdan.yandextree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final int REFRESH_BUTTON = 1;
    private static final String DB_NAME = "mydb";
    private static final int DB_VERSION = 1;
    private static final String SERVICES_TABLE = "services";
    private static final String SERVICE_ID = "serviceId";
    private static final String SERVICE_HEAD = "head";
    private static final String SERVICE = "service";
    private static final String DB_CREATE = "create table " + SERVICES_TABLE + "("
            + SERVICE_ID + " text, "
            + SERVICE + " text, " + SERVICE_HEAD + " text" + ")";
    /*private final String deleteSQL = "DELETE FROM `" + SERVICES_TABLE + "`";*/
    private SQLiteDatabase db;
    private Cursor c;
    private DBHelper dbHelper;
    private ExpandableListAdapter listAdapter;
    private ExpandableListView expListView;
    private List<String> listDataHeader;
    private HashMap<String, List<String>> listDataChild;
    private List<String> headList;
    private HashMap<String, List<String>> childList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new DBHelper(this);
        db = dbHelper.getWritableDatabase();
        c = db.query(SERVICES_TABLE, null, null, null, null, null, null);
        if (c.moveToFirst()) {
            initializeTree(c);
        } else {
            c.close();
            new ParseTask().execute();
        }
    }

    private void initializeTree (Cursor c){
        int serviceNameColIndex = c.getColumnIndex(SERVICE);
        int serviceHeadColIndex = c.getColumnIndex(SERVICE_HEAD);
        headList = new ArrayList<String>();
        childList = new HashMap<>();
        do {
            String head = c.getString(serviceHeadColIndex);
            String child = c.getString(serviceNameColIndex);
            if (!headList.contains(head)) {
                headList.add(head);
                childList.put(head, new ArrayList<String>());
            }
            List<String> value = childList.get(head);
            value.add(child);
            childList.put(head, value);
        } while (c.moveToNext());
        c.close();
        db.close();

        expListView = (ExpandableListView) findViewById(R.id.expandableListView);
        listAdapter = new ExpandableListAdapter(this, headList, childList);
        expListView.setAdapter(listAdapter);
    }

    private class ParseTask extends AsyncTask<Void, Void, String> {
        HttpURLConnection httpURLConnection;
        BufferedReader bufferedReader;
        String resultJson;

        @Override
        protected String doInBackground(Void... params) {
            try {
                URL url = new URL("https://money.yandex.ru/api/categories-list");
                httpURLConnection = (HttpURLConnection)url.openConnection();
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.connect();
                InputStream inputStream = httpURLConnection.getInputStream();
                StringBuilder stringBuilder = new StringBuilder();
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                resultJson = stringBuilder.toString();

            } catch (IOException e) {
                e.printStackTrace();
            }
            return resultJson;
        }

        @Override
        protected void onPostExecute(String stringJson) {
            super.onPostExecute(stringJson);
            ContentValues cv;
            try {
                JSONArray data = new JSONArray(stringJson);
                for (int i = 0; i < data.length(); i++){
                    JSONObject c = data.getJSONObject(i);
                    JSONArray items = new JSONArray(c.getString("subs"));
                    for (int j = 0; j < items.length(); j++){
                        JSONObject d = items.getJSONObject(j);
                        cv = new ContentValues();
                        cv.put(SERVICE_ID, d.getString("id"));
                        cv.put(SERVICE, d.getString("title"));
                        cv.put(SERVICE_HEAD, c.getString("title"));
                        db.insert(SERVICES_TABLE, null, cv);
                    }
                }
                c = db.query(SERVICES_TABLE, null, null, null, null, null, null);
                if (c.moveToFirst()) {
                    initializeTree(c);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    class DBHelper extends SQLiteOpenHelper {

        public DBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DB_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_refresh){
            db = dbHelper.getWritableDatabase();
            db.delete(SERVICES_TABLE, null, null);
            headList.clear();
            childList.clear();
            listAdapter.notifyDataSetChanged();
            new ParseTask().execute();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

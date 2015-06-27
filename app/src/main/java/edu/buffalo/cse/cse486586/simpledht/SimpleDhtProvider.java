package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = "TAG";

    Sqlhelper mydb;
    SQLiteDatabase database;
    ArrayList<String> peerlist = new ArrayList<String>(5);
    HashMap<String, String> porthashmap = new HashMap<>();
    String portStr, myPort;
    String successor, predecessor;

    public static final Uri uri= Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider");

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        try {
            if(successor == portStr) {
                database.insert(Sqlhelper.TABLE_NAME, null, values);
                return uri;
            }
            String keyhash = genHash((String) values.get("key"));
            String myhash = genHash(portStr);
            String predhash = genHash(predecessor);

            if(keyhash.compareTo(predhash) > 0 && keyhash.compareTo(myhash) <= 0) {
                database.insert(Sqlhelper.TABLE_NAME, null, values);
                return uri;
            }
            else if (keyhash.compareTo(predhash) <= 0 && keyhash.compareTo(myhash) <= 0 && predhash.compareTo(myhash) > 0){
                database.insert(Sqlhelper.TABLE_NAME,null,values);
                return uri;
            } else if (keyhash.compareTo(predhash) > 0 && predhash.compareTo(myhash) > 0) {
                database.insert(Sqlhelper.TABLE_NAME,null,values);
                return uri;
            } else {
                ArrayList<String> tosend = new ArrayList<>();
                tosend.add("Insert_Key");
                tosend.add((String) values.get("key"));
                tosend.add((String) values.get("value"));
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,tosend);
                return uri;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Log.v("insert", values.toString());
        return uri;

    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        successor = portStr;
        predecessor = portStr;

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(10000);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
        }

        if(portStr.equals("5554")) {
            try {
                String myhash = genHash(portStr);
                peerlist.add(myhash);
                porthashmap.put(myhash,portStr);
                porthashmap.put(genHash("5556"), "5556");
                porthashmap.put(genHash("5558"), "5558");
                porthashmap.put(genHash("5560"), "5560");
                porthashmap.put(genHash("5562"), "5562");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        } else {
            ArrayList<String> message = new ArrayList<>();
            message.add("Join_Node");
            message.add(portStr);
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,message);
        }
        mydb= new Sqlhelper(getContext());
        database=mydb.getWritableDatabase();
        if(database !=null)
            return true;
        else
            return false;

    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        String args[]={selection};
        if(selection.equals("\"@\"")||selection.equals("\"*\"")){
            return database.rawQuery("Select * from " + Sqlhelper.TABLE_NAME,null);
        }
        Cursor cursor=database.query(Sqlhelper.TABLE_NAME,projection,Sqlhelper.Keyval+"=?",args,null,null,sortOrder);
        Log.v("query",selection);
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private class ClientTask extends AsyncTask<ArrayList<String>, Void, Void> {

        @Override
        protected Void doInBackground(ArrayList<String>... msgs) {
            try {
                String msgToSend = msgs[0].get(0);
                Socket socket;
                if(msgToSend.equals("Join_Node")) {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt("11108"));
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(msgs[0]);
                    out.close();
                    socket.close();
                } else if(msgToSend.equals("Join_Complete")) {
                    for (int i = 1; i <= 3; i++) {
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(msgs[0].get(i))*2);
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        out.writeObject(msgs[0]);
                        out.close();
                        socket.close();
                        Log.i(TAG,"sending from client ---- prev:"+msgs[0].get(3)+"|curr:"+msgs[0].get(1)+"|next:"+msgs[0].get(2));
                    }
                } else {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(String.valueOf((Integer.parseInt(successor) * 2))));
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(msgs[0]);
                    out.close();
                    socket.close();
                }
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */


            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
    private class ServerTask extends AsyncTask<ServerSocket, Void, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            Log.e(TAG, "About to enter the server receive block");
            //my code from PA1
            try {
                Socket inp=null;
                while (true) {
                    inp = serverSocket.accept();

                    ObjectInputStream input = new ObjectInputStream(inp.getInputStream());
                    ArrayList<String> messagelist = (ArrayList<String>)input.readObject();
                    input.close();

                    if(messagelist.get(0).equals("Join_Node")) {
                        Log.e(TAG, "peerlist: " + peerlist);
                        Log.e(TAG, "porthashmap: " + porthashmap);

                        try {
                            String port = messagelist.get(1);
                            String hash = genHash(port);
                            peerlist.add(hash);
                            Collections.sort(peerlist);
                            for(int i=0;i<peerlist.size();i++){
                                Log.e(TAG, "peerlist of "+i+": " + peerlist.get(i));
                                Log.e(TAG, "hash: " + hash);
                                if(hash.equals(peerlist.get(i))) {
                                    if(i==0){
                                        ArrayList<String> tosend = new ArrayList<>();
                                        tosend.add("Join_Complete");
                                        tosend.add(port);
                                        String next = porthashmap.get(peerlist.get(1));
                                        tosend.add(next);
                                        String prev = porthashmap.get(peerlist.get(peerlist.size()-1));
                                        tosend.add(prev);
                                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,tosend);
                                        break;
                                    } else if (i == peerlist.size() - 1){
                                        ArrayList<String> tosend = new ArrayList<>();
                                        tosend.add("Join_Complete");
                                        tosend.add(port);
                                        String next = porthashmap.get(peerlist.get(0));
                                        tosend.add(next);
                                        String prev = porthashmap.get(peerlist.get(peerlist.size()-2));
                                        tosend.add(prev);
                                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,tosend);
                                        break;
                                    } else {
                                        ArrayList<String> tosend = new ArrayList<>();
                                        tosend.add("Join_Complete");
                                        tosend.add(port);
                                        String next = porthashmap.get(peerlist.get(i+1));
                                        tosend.add(next);
                                        String prev = porthashmap.get(peerlist.get(i-1));
                                        tosend.add(prev);
                                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,tosend);
                                        break;
                                    }
                                }
                            }
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                    } else if(messagelist.get(0).equals("Join_Complete")) {
                        String val1 = messagelist.get(1);
                        String val2 = messagelist.get(2);
                        String val3 = messagelist.get(3);

                        if(val1.equals(portStr)) {
                            Log.e(TAG, "I am: " + val1);
                            successor = val2;
                            Log.e(TAG, "Successor is: " + successor);
                            predecessor = val3;
                            Log.e(TAG, "Predecessor is: " + predecessor);
                        } else if (val2.equals(portStr)){
                            Log.e(TAG, "I am: " + val2);
                            predecessor = val1;
                            Log.e(TAG, "Successor is: " + successor);
                            Log.e(TAG, "Predecessor is: " + predecessor);
                        } else {
                            successor = val1;
                            Log.e(TAG, "I am: " + val3);
                            Log.e(TAG, "Successor is: " + successor);
                            Log.e(TAG, "Predecessor is: " + predecessor);
                        }
                    } else if(messagelist.get(0).equals("Insert_Key")) {
                        ContentValues cvalues = new ContentValues();
                        cvalues.put("key", messagelist.get(1));
                        cvalues.put("value", messagelist.get(2));
                        insert(uri,cvalues);
                    }
                    inp.close();
                }
            }
            catch(IOException e)
            {
                Log.e(TAG,"error");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            //my code ends
            return null;
        }
    }
}

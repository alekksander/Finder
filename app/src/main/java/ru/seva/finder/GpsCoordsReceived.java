package ru.seva.finder;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GpsCoordsReceived extends IntentService {
    public GpsCoordsReceived() {
        super("GpsCoordsReceived");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String date;
        Double lat=0d, lon=0d, altitude = null;
        Float speed = null, direction = null;
        Integer acc = null;

        String message = intent.getStringExtra("message");
        String phone = intent.getStringExtra("phone");
        Pattern lat_lon = Pattern.compile("^lat:(-?\\d+\\.\\d+) lon:(-?\\d+\\.\\d+)");
        Matcher m_lat_lon = lat_lon.matcher(message);
        if (m_lat_lon.find()) {
            lat = Double.valueOf(m_lat_lon.group(1));  //инициализируется, инфа-сотка - проверено в ресивере
            lon = Double.valueOf(m_lat_lon.group(2));
        }

        Pattern alt = Pattern.compile("alt:(\\d+\\.?\\d*)");
        Matcher m_alt = alt.matcher(message);
        if (m_alt.find()) {
            altitude = Double.valueOf(m_alt.group(1));
        }

        Pattern spd = Pattern.compile("vel:(\\d+\\.?\\d*)");
        Matcher m_spd = spd.matcher(message);
        if (m_spd.find()) {
            speed = Float.valueOf(m_spd.group(1));
        }

        Pattern dir = Pattern.compile("az:(\\d+\\.?\\d*)");
        Matcher m_dir = dir.matcher(message);
        if (m_dir.find()) {
            direction = Float.valueOf(m_dir.group(1));
        }

        Pattern ac = Pattern.compile("acc:(\\d+)");
        Matcher m_acc = ac.matcher(message);
        if (m_acc.find()) {
            acc = Integer.valueOf(m_acc.group(1));
        }

        dBase baseConnect = new dBase(getApplicationContext());
        SQLiteDatabase db = baseConnect.getWritableDatabase();

        DateFormat df = new SimpleDateFormat("MMM d, HH:mm");
        date = df.format(Calendar.getInstance().getTime());
        MainActivity.write_to_hist(db, phone, lat, lon, acc, date, null, altitude, speed, direction);
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (MainActivity.activityRunning && sPref.getBoolean("auto_map", false)) {  //карта вылетит только в случае настройки
            Intent start_map = new Intent(getApplicationContext(), MapsActivity.class);
            start_map.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            start_map.putExtra("lat", lat);
            start_map.putExtra("lon", lon);
            start_map.putExtra("zoom", 15);
            if (acc != null) {
                start_map.putExtra("accuracy", String.valueOf(acc) + getString(R.string.meters));
            }
            startActivity(start_map);
        } else {
            Intent intentRes = new Intent(getApplicationContext(), HistoryActivity.class);
            PendingIntent pendIntent = PendingIntent.getActivity(getApplicationContext(), 0, intentRes, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.message_with_coord))
                    .setContentText(getString(R.string.coords_received) + phone)
                    .setAutoCancel(true)
                    .setContentIntent(pendIntent);  //подумать над channel id  и ИКОНКОЙ!
            Notification notification = builder.build();
            NotificationManager nManage = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            int id = sPref.getInt("notification_id", 0);
            nManage.notify(id, notification);
            sPref.edit().putInt("notification_id", id+1).commit();  //это и так новый поток
        }
    }
}
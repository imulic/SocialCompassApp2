package com.example.irfanmulic.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.hardware.SensorManager;

import android.util.Log;
import android.view.View;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    //http://stackoverflow.com/questions/4308262/calculate-compass-bearing-heading-to-location-in-android
    //http://stackoverflow.com/questions/27730173/determine-compass-direction
    //http://stackoverflow.com/questions/5479753/using-orientation-sensor-to-point-towards-a-specific-location

    //map for bearing http://gistools.igismap.com/bearing

    //https://github.com/iutinvg/compass
    //https://www.journal.deviantdev.com/android-compass-azimuth-calculating/
    //https://www.youtube.com/watch?v=C7JQ7Rpwn2k


    Float azimut; // from magnetic field sensor
    double bearing = 0.0d;
    Float heading = 0.0f; // heading calculation

    CustomDrawableView mCustomDrawableView;
    private SensorManager mSensorManager;
    private LocationManager mLocationManager;
    Location myGPSLocation;
    Sensor accelerometer;
    Sensor magnetometer;
    float[] mGravity;
    float[] mGeomagnetic;
    Vibrator v;

    GeomagneticField geoField; // required for calculation of declination difference between magnetic north and true north
    ArrayList<Person> person_locations = new ArrayList<Person>();
    Person myLocation = new Person("irfan", 32.65702666004866d, -116.9703197479248d, "San Diego"); // hard coded due to missing GPS on my phone since the service wasn't activated
    Person found; // pointer to a person that is closest to the orientation of the phone

    // Function to convert bearing from -180 to 180 to 0-360
    private float normalizeDegree(float value){
        if (value>=0.0f && value <= 180.0f){
            return value;
        } else {
            return 360+value;
        }
    }

    // Draw lines showing stuff
    public class CustomDrawableView extends View {
        Paint paint = new Paint();

        public CustomDrawableView(Context context) {
            super(context);
            paint.setColor(0xff00ff00);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setAntiAlias(true);

            paint.setTypeface(Typeface.create("Arial", Typeface.ITALIC));
            paint.setTextSize(30);
            paint.setStyle(Style.FILL);
        }

        protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            int centerx = width / 2;
            int centery = height / 2;
            canvas.drawLine(centerx, 0, centerx, height, paint);
            canvas.drawLine(0, centery, width, centery, paint);

            // float dist = calculateDistanceFrom(myParentsLocation[0], myParentsLocation[1], me[0], me[1]);
            double angle_difference = 0.0d;

            if (azimut != null)
                angle_difference = (360 + ((bearing + 360) % 360) - (azimut * (180 / 3.14159f))) % 360;

            // canvas.drawText("MY LOCATION",centerx, centery-40, paint);
            canvas.drawText("Azimut: " + String.format("%4.2f", azimut), centerx + 30, centery - 10, paint);
            canvas.drawText("Bearing: " + String.format("%4.2f", bearing), centerx, centery - 70, paint);
            canvas.drawText("Angle Delta: " + String.format("%4.2f", angle_difference), centerx, centery - 100, paint);

            // if a person's myGPSLocation is aligned with our bearing
            if (found != null) {
                paint.setColor(Color.RED);
                canvas.drawCircle(centerx, centery, 50, paint);
                canvas.drawText(found.getName() + " @ " + found.getPlace(), centerx, centery + 20, paint);
                Log.i("GOT IT !", "right direction found.");
                //vibrate();
                paint.setColor(0xff00ff00);
            }

            if (azimut != null)
                canvas.rotate(-azimut * 360 / (2 * 3.14159f), centerx, centery); // radians of azimuth to degrees


            paint.setColor(0xff0000ff);
            canvas.drawLine(centerx, -1000, centerx, +1000, paint);
            canvas.drawLine(-1000, centery, 1000, centery, paint);
            canvas.drawText("N", centerx + 5, centery - 10, paint);
            canvas.drawText("S", centerx-10, centery+15, paint);
            paint.setColor(0xff00ff00);

        }
    }

    protected double getDeltaAngle(float compassHeading, float gpsAngle) {
        double deltaAngle = Math.floor(compassHeading - gpsAngle);
        if (deltaAngle < 0) {
            deltaAngle += 360;
        }
        return deltaAngle;
    }

    protected static double getBearing(double lat1, double lon1, double lat2, double lon2) {
        double longitude1 = lon1;
        double longitude2 = lon2;
        double latitude1 = Math.toRadians(lat1);
        double latitude2 = Math.toRadians(lat2);
        double longDiff = Math.toRadians(longitude2 - longitude1);
        double y = Math.sin(longDiff) * Math.cos(latitude2);
        double x = Math.cos(latitude1) * Math.sin(latitude2) - Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longDiff);

        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    protected double calculateBearing(Person p) {
        Location loc;   //Will hold lastknown myGPSLocation
        Location wptLoc = new Location("");    // Waypoint myGPSLocation
        double dist = -1;
        double bearing = 0;
        double heading = 0;
        double arrow_rotation = 0;

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (loc == null) {   //No recent GPS fix
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(false);
            criteria.setBearingRequired(true);
            criteria.setCostAllowed(true);
            criteria.setSpeedRequired(false);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return 0.0f;
            }
            loc = lm.getLastKnownLocation(lm.getBestProvider(criteria, true));

        }

        if (loc != null) {
            wptLoc.setLongitude(p.getLon());
            wptLoc.setLatitude(p.getLat());

            dist = loc.distanceTo(wptLoc);
            bearing = loc.bearingTo(wptLoc);    // -180 to 180
            Log.i("myGPSLocation bearing: ", String.format("%4.2f", bearing));

            heading = loc.getBearing();         // 0 to 360
            // *** Code to calculate where the arrow should point ***
            arrow_rotation = (360 + ((bearing + 360) % 360) - heading) % 360;
        }
        return bearing;
    }

    protected void refreshFavoriteLocations() {

        String momUrl = "http://localhost:3000/api/important_locations?filter=%7B%22id%22%3A1%7D";
        String all_locations = "http://9cb4a4e5.ngrok.io/api/important_locations";

        JsonArrayRequest jsonRequest = new JsonArrayRequest
                (Request.Method.GET, all_locations, null, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        person_locations.clear();

                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject person = (JSONObject) response
                                        .get(i);

                                String name = person.getString("name"),
                                        lon = person.getString("lon"),
                                        lat = person.getString("lat"),
                                        city = person.getString("place");


                                person_locations.add(new Person(name, Double.valueOf(lat), Double.valueOf(lon), city));

                                //Log.i("PERSON", p.toString());
                                System.out.println("Response from the server: Name: " + name + "\nCity: " + city + "\nLon" + lon + "\nLat" + lat);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });

        Volley.newRequestQueue(this).add(jsonRequest);
    }

    protected void vibrate() {
        v.vibrate(400);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //  setContentView(R.layout.activity_main);
        mCustomDrawableView = new CustomDrawableView(this);
        setContentView(mCustomDrawableView);    // Register the sensor listeners
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        myGPSLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (myGPSLocation != null && myGPSLocation.getTime() > Calendar.getInstance().getTimeInMillis() - 2 * 60 * 1000) {
            // Do something with the recent myGPSLocation fix
            //  otherwise wait for the update below
        } else {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) this);
        }

        // Refresh myGPSLocation data that we are interested in from the server
        final Handler h1 = new Handler();
        Runnable runData = new Runnable() {
            @Override
            public void run() {
                refreshFavoriteLocations();
                h1.postDelayed(this, 10000);
            }
        };

        h1.post(runData);

        // Run the refresh on schedule
        final Handler handlerRefreshLocations = new Handler();
        Runnable runLoc = new Runnable() {
            @Override
            public void run() {

                if (person_locations != null && person_locations.size() > 0) {
                    bearing = getBearing(myLocation.lat, myLocation.lon, person_locations.get(0).lat, person_locations.get(0).lon);

                    //Float bearingFromGPS = calculateBearing(person_locations.get(0));
                    Location targetLocation = new Location("");
                    targetLocation.setLatitude(person_locations.get(0).getLat());
                    targetLocation.setLongitude(person_locations.get(0).getLon());
                    Log.i("target location gps ", targetLocation.toString());

                    Float myBearing = myGPSLocation.bearingTo(targetLocation);
                    Log.i("MyBearingFromLocation",myBearing.toString());

                    heading = azimut;

                    //heading = azimut * (180/3.14159f);
                    Log.i("HEADING using azimut:",heading.toString());

                    float declination = 0.0f;

                    if (geoField != null)
                        declination = geoField.getDeclination();

                    heading += declination;

                    heading = myBearing - (myBearing + heading);

                    // heading = normalizeDegree(heading);
                    //Float normalizedBearing = normalizeDegree(bearingFromGPS);
                    //Log.i("GPS BEARING",bearingFromGPS.toString());
                    Log.i("HEADING normal deg",heading.toString());


                    // calculation using azimut
                    Double angle_difference = 0.0d;
                    double angle_difference_gps = 0.0d;



                    /*if (azimut != null)
                        angle_difference = (360 + ((bearing + 360) % 360) - azimut) % 360;
                    */

                    if (azimut != null)
                        angle_difference = (360 + ((bearing + 360) % 360) - (azimut * (180 / 3.14159f))) % 360;


                    Log.i("azimut : ", String.format("%4.2f", azimut));
                    Log.i("bearing: ", String.format("%4.2f", bearing));
                    Log.i("angle difference: ", String.format("%4.2f", angle_difference));

                    if (Math.abs(angle_difference) < 45d) {
                        found = person_locations.get(0);
                        Log.i("PERSON FOUND", found.toString());
                        vibrate();
                        mCustomDrawableView.invalidate();
                    } else {
                        found = null;
                        v.cancel();
                    }
                }

                handlerRefreshLocations.postDelayed(this, 2000);

            }
        };
        handlerRefreshLocations.post(runLoc);

        // Run the draw every 250 sec
        final Handler handler = new Handler();
        Runnable runDraw = new Runnable() {
            @Override
            public void run() {
                mCustomDrawableView.invalidate();
                handler.postDelayed(this, 250);
            }
        };

        handler.post(runDraw);

    }

    public static float calculateDistanceFrom(float lat1, float lng1, float lat2, float lng2) {
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        float dist = (float) (earthRadius * c);

        return dist;
    }


    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimut = orientation[0]; // orientation contains: azimut, pitch and roll
                Log.i("AZIMUT RAW RAD",String.format("%4.4f",azimut));
                float azimutInDegrees = (float)(Math.toDegrees(azimut));
                Log.i("AZIMUT DEGREES",String.format("%4.4f",normalizeDegree(azimutInDegrees)));
                //azimut = Math.toDegrees(orientation[0]);
            }
        }
        ///mCustomDrawableView.invalidate();
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    // Required functions
    public void onProviderDisabled(String arg0) {
    }

    public void onProviderEnabled(String arg0) {
    }

    @Override
    public void onLocationChanged(Location location) {

        geoField = new GeomagneticField(
                Double.valueOf(location.getLatitude()).floatValue(),
                Double.valueOf(location.getLongitude()).floatValue(),
                Double.valueOf(location.getAltitude()).floatValue(),
                System.currentTimeMillis()
        );

        if (location != null) {
            Log.v("Location Changed", location.getLatitude() + " and " + location.getLongitude());
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mLocationManager.removeUpdates(this);
        }
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}
}

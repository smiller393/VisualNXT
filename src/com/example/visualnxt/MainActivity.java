package com.example.visualnxt;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import java.lang.Object;
import java.lang.Comparable;
import android.os.Environment;
import java.io.File;
import android.util.Log;
import android.view.*;

public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Environment.getExternalStorageDirectory();
        // create a File object for the parent directory
        createDirIfNotExists("/VisualNXT");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    public void openCamera(View view) {
    	
    }
    
    public static boolean createDirIfNotExists(String path) {
        boolean ret = true;

        File file = new File(Environment.getExternalStorageDirectory(), path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Log.e("TravellerLog :: ", "Problem creating Image folder");
                ret = false;
            }
        }
        return ret;
    }
}

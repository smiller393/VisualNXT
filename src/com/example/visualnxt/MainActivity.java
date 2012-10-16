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
import android.content.*;
import android.provider.MediaStore;
import android.net.*;
import android.widget.*;

public class MainActivity extends Activity {

	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    public void callCamera(View view){
    	Intent intent = new Intent(this, CameraActivity.class);
    	startActivity(intent);	
    }
    
    public void callFileDisplay(View view){
    	Intent intent = new Intent(this, FileDisplay.class);
    	startActivity(intent);
    }
    	
    public void callUpload(View view){
        Intent intent = new Intent(this, UniversalUploader.class);
        startActivity(intent);	
    }
    
    
    
    
    

    
}

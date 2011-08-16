/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package ros.android.makeamap;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Toast;
import org.ros.node.Node;
import org.ros.node.service.ServiceResponseListener;
import org.ros.exception.RosException;
import org.ros.exception.RemoteException;
import org.ros.node.service.ServiceClient;
import org.ros.namespace.NameResolver;
import org.ros.service.map_store.NameLatestMap;
import ros.android.activity.RosAppActivity;
import ros.android.views.SensorImageView;
import ros.android.views.MapView;
import ros.android.views.JoystickView;
import android.widget.Toast;

/**
 * @author kwc@willowgarage.com (Ken Conley)
 * @author hersh@willowgarage.com (Dave Hershberger)
 */
public class MakeAMap extends RosAppActivity {
  private JoystickView joystickView;
  private SensorImageView cameraView;
  private MapView mapView;
  private ViewGroup mainLayout;
  private ViewGroup sideLayout;
  private String robotAppName;
  private String baseControlTopic;
  private String cameraTopic;
  private static final int NAME_MAP_DIALOG_ID = 0;

  private enum ViewMode {
    CAMERA, MAP
  };

  private ViewMode viewMode;
  private boolean deadman;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    setDefaultAppName("turtlebot_teleop/android_make_a_map");
    setDashboardResource(R.id.top_bar);
    setMainWindowResource(R.layout.main);
    super.onCreate(savedInstanceState);

    joystickView = (JoystickView)findViewById(R.id.joystick);
    if (getIntent().hasExtra("base_control_topic")) {
      joystickView.setBaseControlTopic(getIntent().getStringExtra("base_control_topic"));
    }

    if (getIntent().hasExtra("camera_topic")) {
      cameraTopic = getIntent().getStringExtra("camera_topic");
    } else {
      cameraTopic = "camera/rgb/image_color/compressed_throttle";
    }
    
    mapView = (MapView) findViewById(R.id.map_view);
    if (getIntent().hasExtra("footprint_param")) {
      mapView.setFootprintParam(getIntent().getStringExtra("footprint_param"));
    }
    if (getIntent().hasExtra("base_scan_topic")) {
      mapView.setBaseScanTopic(getIntent().getStringExtra("base_scan_topic"));
    }
    if (getIntent().hasExtra("base_scan_frame")) {
      mapView.setBaseScanFrame(getIntent().getStringExtra("base_scan_frame"));
    }

    cameraView = (SensorImageView) findViewById(R.id.image);


    mainLayout = (ViewGroup) findViewById(R.id.main_layout);
    sideLayout = (ViewGroup) findViewById(R.id.side_layout);

    viewMode = ViewMode.CAMERA;

    mapView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        MakeAMap.this.swapViews();
      }
    });
    cameraView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        MakeAMap.this.swapViews();
      }
    });
    mapView.setClickable(true);
    cameraView.setClickable(false);
  }

  public void saveMap(View view) {
    showDialog(NAME_MAP_DIALOG_ID);
  }
  
  public void refreshMap(View view) {
    mapView.refreshMap();
  }
  
  /**
   * Swap the camera and map views.
   */
  private void swapViews() {
    // Figure out where the views were...
    ViewGroup mapViewParent;
    ViewGroup cameraViewParent;
    Log.i("MakeAMap", "viewMode = " + viewMode);
    if (viewMode == ViewMode.CAMERA) {
      Log.i("MakeAMap", "camera mode");
      mapViewParent = sideLayout;
      cameraViewParent = mainLayout;
    } else {
      Log.i("MakeAMap", "map mode");
      mapViewParent = mainLayout;
      cameraViewParent = sideLayout;
    }
    int mapViewIndex = mapViewParent.indexOfChild(mapView);
    int cameraViewIndex = cameraViewParent.indexOfChild(cameraView);

    // Remove the views from their old locations...
    mapViewParent.removeView(mapView);
    cameraViewParent.removeView(cameraView);

    // Add them to their new location...
    mapViewParent.addView(cameraView, mapViewIndex);
    cameraViewParent.addView(mapView, cameraViewIndex);

    // Remeber that we are in the other mode now.
    if (viewMode == ViewMode.CAMERA) {
      viewMode = ViewMode.MAP;
    } else {
      viewMode = ViewMode.CAMERA;
    }
    mapView.setClickable(viewMode != ViewMode.MAP);
    cameraView.setClickable(viewMode != ViewMode.CAMERA);
  }

  @Override
  protected void onNodeDestroy(Node node) {
    deadman = false;
    if (cameraView != null) {
      cameraView.stop();
      cameraView = null;
    }
    if (joystickView != null) {
      joystickView.stop();
      joystickView = null;
    }
    mapView.stop();
    super.onNodeDestroy(node);
  }

  @Override
  protected void onResume() {
    super.onResume();
    Toast.makeText(MakeAMap.this, "starting app", Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onNodeCreate(Node node) {
    Log.i("MakeAMap", "startAppFuture");
    super.onNodeCreate(node);
    try {
      mapView.start(node);
      NameResolver appNamespace = getAppNamespace(node);
      cameraView = (SensorImageView) findViewById(R.id.image);
      Log.i("MakeAMap", "init cameraView");
      cameraView.start(node, appNamespace.resolve(cameraTopic).toString());
      cameraView.post(new Runnable() {

        @Override
        public void run() {
          cameraView.setSelected(true);
        }
      });
      joystickView.start(node);
    } catch (RosException ex) {
      safeToastStatus("Failed: " + ex.getMessage());
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.make_a_map_options, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.kill:
      android.os.Process.killProcess(android.os.Process.myPid());
      return true;
    case R.id.name_map:
      showDialog(NAME_MAP_DIALOG_ID);
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog;
    Button button;
    switch (id) {
    case NAME_MAP_DIALOG_ID:
      dialog = new Dialog(this);
      dialog.setContentView(R.layout.name_map_dialog);
      dialog.setTitle("Set map name");

      final EditText nameField = (EditText) dialog.findViewById(R.id.name_editor);
      nameField.setOnKeyListener(new View.OnKeyListener() {
          @Override
          public boolean onKey(View view, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
              String newName = nameField.getText().toString();
              if (newName != null && newName.length() > 0) {
                setMapName(newName);
              }
              dismissDialog(NAME_MAP_DIALOG_ID);
              return true;
            } else {
              return false;
            }
          }
        });

      button = (Button) dialog.findViewById(R.id.cancel_button);
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          dismissDialog(NAME_MAP_DIALOG_ID);
        }
      });
      break;
    default:
      dialog = null;
    }
    return dialog;
  }
  
  private void setMapName(final String newName) {
    try {
      Log.i("MakeAMap", "Map should soon be named " + newName);
      int debug = 0;
      ServiceClient<NameLatestMap.Request, NameLatestMap.Response> nameMapServiceClient =
        getNode().newServiceClient("name_latest_map", "map_store/NameLatestMap");
      NameLatestMap.Request nameMapRequest = new NameLatestMap.Request();
      nameMapRequest.map_name = newName;
      nameMapServiceClient.call(nameMapRequest, new ServiceResponseListener<NameLatestMap.Response>() {
          @Override public void onSuccess(NameLatestMap.Response message) {
            Log.i("MakeAMap", "setMapName() Success ");
            // TODO: put success/failure info into response and show it.
            safeToastStatus("Map has been named " + newName);
          }
          
          @Override public void onFailure(RemoteException e) {
            Log.i("MakeAMap", "setMapName() Failure");
            safeToastStatus("Naming map failed: " + e.getMessage());
          }
        });
    } catch(Throwable ex) {
      Log.e("MakeAMap", "setMapName() caught exception: " + ex.toString());
      safeToastStatus("Naming map couldn't even start: " + ex.getMessage());
    }
  }
}
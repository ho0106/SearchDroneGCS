package com.example.petsearch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.NaverMapSdk;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;

import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ExperimentalApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.solo.SoloCameraApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.utils.video.DecoderListener;
import com.o3dr.android.client.utils.video.MediaCodecManager;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.droidplanner.services.android.impl.utils.video.VideoManager;

import java.io.IOException;
import java.util.*;

import static com.o3dr.android.client.apis.ExperimentalApi.getApi;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, View.OnClickListener, DroneListener, TowerListener {
    // NaverMap
    private NaverMap mNaverMap;
    private Marker droneMarker = new Marker();
    private List<LatLng> poly = new ArrayList<>();
    private PolylineOverlay polylineOverlay = new PolylineOverlay();
    boolean mMapLock = true;

    // FAB UI
    private Animation fab_open, fab_close;
    private FloatingActionButton mFabMain, mFabBasic, mFabNavi, mFabSatellite, mFabHybrid, mFabTerrain;
    private Boolean openFlag = false;

    // DroneLogList
    private RecyclerView mDroneRecyclerView;
    private DroneLog mDroneLog;
    private ArrayList mDroneDataLog = new ArrayList();

    // Drone
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private Spinner modeSelector;
    private final Handler handler = new Handler();
    private double mDroneAltitude = 5.0;
    private Gps mGps;
    private Attitude mDroneYaw;
    private Float mYaw;

    boolean mClearValue = true;

    // Search Mission
    private List<Marker> mSearchMarker = new ArrayList<>();
    private List<Marker> mPolygonMarker = new ArrayList<>();
    private List<LatLng> mSearchPolygon = new ArrayList<>();
    private List<LatLng> mSearchPolyline = new ArrayList<>();

    // Mission Overlay
    private PolygonOverlay mSearchPolygonOverlay = new PolygonOverlay();
    private PolylineOverlay mSearchPolylineOverlay = new PolylineOverlay();
    private int mSearchMarkerCount = 0;
    private int mGapCount = 0;
    private int mReachedCount = 1;

    // Mission distance,width init
    private double mFlightWidth = 5.5; // 최초 비행폭 5.5M
    private int mABDistance = 50; // 최초 AB거리값 50M

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Full screen //
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;
        boolean isImmersiveModeEnabled = ((uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions);
        if (isImmersiveModeEnabled) {
            Log.i("Is on?", "Turning immersive mode mode off. ");
        } else {
            Log.i("Is on?", "Turning immersive mode mode on.");
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);

        // 네이버 맵 getAPI //
        NaverMapSdk.getInstance(this).setClient(
                new NaverMapSdk.NaverCloudPlatformClient("895cz3v0pt")
        );

        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment)fm.findFragmentById(R.id.naverMap);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.naverMap, mapFragment).commit();
        }

        mapFragment.getMapAsync(this);

        // Drone Log //
        mDroneRecyclerView = findViewById(R.id.droneLog);
        LinearLayoutManager mDroneLinerLayoutManager = new LinearLayoutManager(this);

        mDroneLinerLayoutManager.setReverseLayout(true);
        mDroneLinerLayoutManager.setStackFromEnd(true);
        mDroneRecyclerView.setLayoutManager(mDroneLinerLayoutManager);
        mDroneDataLog = new ArrayList<Integer>(10);
        mDroneLog = new DroneLog(mDroneDataLog);
        mDroneRecyclerView.setAdapter(mDroneLog);
        mDroneLog.notifyDataSetChanged();

        // Drone Start //
        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        // Drone Mode Spinner //
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
                ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // FAB 버튼 컨트롤 //
        fab_open = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_close);
        mFabMain = findViewById(R.id.fabMain);
        mFabBasic = findViewById(R.id.fabBasic);
        mFabNavi = findViewById(R.id.fabNavi);
        mFabSatellite = findViewById(R.id.fabSatellite);
        mFabHybrid = findViewById(R.id.fabHybrid);
        mFabTerrain = findViewById(R.id.fabTerrain);

        mFabBasic.startAnimation(fab_close);
        mFabNavi.startAnimation(fab_close);
        mFabSatellite.startAnimation(fab_close);
        mFabHybrid.startAnimation(fab_close);
        mFabTerrain.startAnimation(fab_close);

        mFabBasic.setClickable(false);
        mFabNavi.setClickable(false);
        mFabSatellite.setClickable(false);
        mFabHybrid.setClickable(false);
        mFabTerrain.setClickable(false);

        mFabMain.setOnClickListener(this);
        mFabBasic.setOnClickListener(this);
        mFabNavi.setOnClickListener(this);
        mFabSatellite.setOnClickListener(this);
        mFabHybrid.setOnClickListener(this);
        mFabTerrain.setOnClickListener(this);

        FloatingActionButton fab = findViewById(R.id.fabMain);

        // 지적편집도 //
        final CheckBox cb1 = (CheckBox) findViewById(R.id.checkBox);
        cb1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cb1.isChecked() == true) {
                    mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                    alertUser("지적편집도 활성화");
                } else if (cb1.isChecked() == false) {
                    mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                    alertUser("지적편집도 비활성화");
                }
            }
        });
    }

    @UiThread
    @Override
    public void onMapReady(@NonNull final NaverMap naverMap) {
        this.mNaverMap = naverMap;
        UiSettings uiSettings = naverMap.getUiSettings();
        alertUser("맵 로딩 완료");

        // 최초 위치, 줌 설정 //
        CameraPosition cameraPosition = new CameraPosition(
                new LatLng(35.9424531, 126.6811309), // 대상 지점
                17 // 줌 레벨
        );
        naverMap.setCameraPosition(cameraPosition);

        // UI 숨김 //
        naverMap.setIndoorEnabled(true); // 건물 내부 정보 활성화
        uiSettings.setCompassEnabled(false); // 나침반 숨김
        uiSettings.setZoomControlEnabled(false); // 줌 버튼 숨김
        naverMap.setMapType(NaverMap.MapType.Satellite); // 로딩 지도 위성
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRANSIT, true); // 레이어 그룹 설정
    }

    // Drone Start //

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateUI(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    // DroneKit-Android Listener //

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKiT-Android Interrupted");
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null) {
            alertUser("Unable to retrieve the solo state.");
        } else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    // Drone Listener //

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        State droneState = (State) drone.getAttribute(AttributeType.STATE);
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateUI(this.drone.isConnected());
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateUI(this.drone.isConnected());
                clearValue();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateUI(this.drone.isConnected());
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateVoltage();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.GPS_COUNT:
                updateSatellitesCount();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYaw();
                break;

            case AttributeEvent.HOME_UPDATED:
                //updateDistanceFromHome();
                break;

            case AttributeEvent.GPS_POSITION:
                updateDroneLocation();
                break;

            case AttributeEvent.MISSION_SENT:
                sendMission();
                break;

            case AttributeEvent.MISSION_ITEM_REACHED:
                alertUser(mReachedCount + "번 WayPoint 도착 : " + mReachedCount + " / " + mSearchPolyline.size());
                mReachedCount++;
                break;

            case AttributeEvent.AUTOPILOT_ERROR:
                String error;
                error = extras.getString(AttributeEventExtra.EXTRA_AUTOPILOT_ERROR_ID, droneState.getAutopilotErrorId());
                alertUserError(error);
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    // UI Events //

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            ConnectionParameter connectionParams = selectedConnectionType == ConnectionType.TYPE_UDP
                    ? ConnectionParameter.newUsbConnection(null)
                    : ConnectionParameter.newUdpConnection(null);

            this.drone.connect(connectionParams);
        }
    }

    public void onFlightModeSelected(View view) {
        final VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser(String.format("비행 모드 변경 : %s", vehicleMode.getLabel()));
            }

            @Override
            public void onError(int executionError) {
                alertUserError("비행 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        View dialogView = getLayoutInflater().inflate(R.layout.custom_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(dialogView);

        final AlertDialog alertDialog = builder.create();

        if (vehicleState.isFlying()) {
            onArmButtonFunction(mDroneAltitude);
        } else if (vehicleState.isArmed()) {
            TextView title = dialogView.findViewById(R.id.title);
            title.setText("지정한 이륙고도까지 기체가 상승합니다.");
            TextView message = dialogView.findViewById(R.id.message);
            message.setText("안전거리를 유지하세요.");
            Button btnPositive = dialogView.findViewById(R.id.btnPositive);
            btnPositive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onArmButtonFunction(mDroneAltitude);
                    alertDialog.dismiss();
                }
            });
            Button btnNegative = dialogView.findViewById(R.id.btnNegative);
            btnNegative.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                }
            });
            alertDialog.show();
        } else if (!vehicleState.isConnected()) {
            alertUserError("먼저 드론을 연결해주세요.");
        } else {
            TextView title = dialogView.findViewById(R.id.title);
            title.setText("모터를 가동합니다.");
            TextView message = dialogView.findViewById(R.id.message);
            message.setText("모터가 고속으로 회전합니다.");
            Button btnPositive = dialogView.findViewById(R.id.btnPositive);
            btnPositive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onArmButtonFunction(mDroneAltitude);
                    alertDialog.dismiss();
                }
            });
            Button btnNegative = dialogView.findViewById(R.id.btnNegative);
            btnNegative.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alertDialog.dismiss();
                }
            });
            alertDialog.show();
        }
    }

    public void onArmButtonFunction(double setAltitude) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        this.mDroneAltitude = setAltitude;

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUserError("착륙이 불가능합니다.");
                }

                @Override
                public void onTimeout() {
                    alertUserError("시간초과. (Land)");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(setAltitude, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser(String.format("이륙합니다. 설정된 이륙 고도 : %2.1fm", mDroneAltitude));
                }

                @Override
                public void onError(int executionError) {
                    alertUserError("이륙이 불가능 합니다.");
                }

                @Override
                public void onTimeout() {
                    alertUserError("시간초과. (Take off)");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUserError("드론을 연결해주세요.");
        } else {
            // Connected but not Armed
            VehicleApi.getApi(this.drone).arm(true, false, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("모터 시동");
                }
                @Override
                public void onError(int executionError) {
                    alertUserError("시동을 걸 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    alertUserError("시간초과. (ARM)");
                }
            });
        }
    }

    public void onClearButtonTap(View view) {
        if (this.drone.isConnected()) {
            alertUser("모든 데이터 삭제");

            poly.removeAll(poly);
            polylineOverlay.setMap(null);
            mSearchPolylineOverlay.setMap(null);
            mSearchPolygonOverlay.setMap(null);

            if (mSearchMarker.size() != 0) {
                for (int i = 0; i < mSearchMarker.size(); i++) {
                    mSearchMarker.get(i).setMap(null);
                }
            }

            poly.clear();
            mSearchMarker.clear();
            mPolygonMarker.clear();
            mSearchPolyline.clear();
            mSearchPolygon.clear();

            mSearchMarkerCount = 0;
            mGapCount = 0;
            mReachedCount = 1;

            Button sendMissionButton = (Button) findViewById(R.id.btnSendMission);
            sendMissionButton.setVisibility(View.INVISIBLE);

            TextView sendMissionValue = (TextView) findViewById(R.id.btnSendMission);
            sendMissionValue.setText(R.string.mission_send);
        } else {
            alertUserError("먼저 드론을 연결해 주세요.");
        }
    }

    public void onMapMoveButtonTap(View view) {
        TextView mapMoveValue = findViewById(R.id.btnMapMove);

        if (mMapLock == true) {
            mapMoveValue.setText("드론추적");
            alertUser("추적 해제");
            mMapLock = false;
        } else {
            mapMoveValue.setText("추적 해제");
            alertUser("드론추적");
            mMapLock = true;
        }
    }

    public void onABDistanceTap(View view) {
        Button upDistanceButton = (Button) findViewById(R.id.btnUpDistance);
        Button downDistanceButton = (Button) findViewById(R.id.btnDownDistance);

        if (upDistanceButton.getVisibility() == View.GONE) {
            upDistanceButton.setVisibility(View.VISIBLE);
            downDistanceButton.setVisibility(View.VISIBLE);
        } else {
            upDistanceButton.setVisibility(View.GONE);
            downDistanceButton.setVisibility(View.GONE);
        }
    }

    public void onSetABDistanceTap(View view) {
        TextView ABDistanceValue = (TextView) findViewById(R.id.btnABDistance);

        switch (view.getId()) {
            case R.id.btnUpDistance:
                mABDistance += 10;
                ABDistanceValue.setText(String.format("%dm\n비행 거리", mABDistance));
                break;
            case R.id.btnDownDistance:

                if (mABDistance > 0) {
                    mABDistance -= 10;
                    ABDistanceValue.setText(String.format("%dm\n비행 거리", mABDistance));
                }
                break;
        }
    }

    public void onBtnFlightWidthTap(View view) {
        Button upWidthButton = (Button) findViewById(R.id.btnUpWidth);
        Button downWidthButton = (Button) findViewById(R.id.btnDownWidth);

        if (upWidthButton.getVisibility() == View.GONE) {
            upWidthButton.setVisibility(View.VISIBLE);
            downWidthButton.setVisibility(View.VISIBLE);
        } else {
            upWidthButton.setVisibility(View.GONE);
            downWidthButton.setVisibility(View.GONE);
        }
    }

    public void onBtnSetFlightWidthTap(View view) {
        TextView widthValue = (TextView) findViewById(R.id.btnFlightWidth);

        switch (view.getId()) {
            case R.id.btnUpWidth:
                mFlightWidth += 0.5;
                widthValue.setText(String.format("%2.1fm\n비행 폭", mFlightWidth));
                break;
            case R.id.btnDownWidth:
                if (mFlightWidth > 0) {
                    mFlightWidth -= 0.5;
                    widthValue.setText(String.format("%2.1fm\n비행 폭", mFlightWidth));
                }
                break;
        }
    }

    public void onBtnTakeOffAltitudeTap(View view) {
        Button upAltitudeButton = (Button) findViewById(R.id.btnUpAltitude);
        Button downAltitudeButton = (Button) findViewById(R.id.btnDownAltitude);

        if (upAltitudeButton.getVisibility() == view.GONE) {
            upAltitudeButton.setVisibility(View.VISIBLE);
            downAltitudeButton.setVisibility(View.VISIBLE);
        } else {
            upAltitudeButton.setVisibility(View.GONE);
            downAltitudeButton.setVisibility(View.GONE);
        }
    }

    public void onBtnSetAltitudeTap(View view) {
        TextView altitudeValue = findViewById(R.id.btnTakeOffAltitude);

        switch (view.getId()) {
            case R.id.btnUpAltitude:
                if (mDroneAltitude < 9.51) {
                    mDroneAltitude += 0.5;
                    altitudeValue.setText(String.format("%2.1fm\n이륙고도", mDroneAltitude));
                    alertUser(String.format("이륙 고도 변경 : %2.1fm", mDroneAltitude));
                } else if (mDroneAltitude >= 10.0) {
                    alertUserError("고도 10m이상 설정 불가.");
                }
                break;
            case R.id.btnDownAltitude:
                if (mDroneAltitude >= 3.5) {
                    mDroneAltitude -= 0.5;
                    altitudeValue.setText(String.format("%2.1fm\n이륙고도", mDroneAltitude));
                    alertUser(String.format("이륙 고도 변경 : %2.1fm", mDroneAltitude));
                } else if (mDroneAltitude <= 3.49) {
                    alertUserError("고도 3m이하 설정 불가");
                }
                break;
        }
    }

    public void onBtnSetRangeTap(View view) {
        Button setSearchRangeButton = (Button) findViewById(R.id.btnSetSearchRange);
        Button setPolygonRangeButton = (Button) findViewById(R.id.btnSetPolygonRange);
        Button missionCancelButton = (Button) findViewById(R.id.btnMissionCancel);

        if(setSearchRangeButton.getVisibility() == view.GONE) {
            setSearchRangeButton.setVisibility(View.VISIBLE);
            setPolygonRangeButton.setVisibility(View.VISIBLE);
            missionCancelButton.setVisibility(View.VISIBLE);
        } else {
            setSearchRangeButton.setVisibility(View.GONE);
            setPolygonRangeButton.setVisibility(View.GONE);
            missionCancelButton.setVisibility(View.GONE);
        }
    }

    public void onBtnSearchRangeTap(View view) {
        Button setSearchRangeButton = (Button) findViewById(R.id.btnSetSearchRange);
        Button missionCancelButton = (Button) findViewById(R.id.btnMissionCancel);
        Button sendMissionButton = (Button) findViewById(R.id.btnSendMission);
        Button setPolygonRangeButton = (Button) findViewById(R.id.btnSetPolygonRange);
        TextView setRangeValue = (TextView) findViewById(R.id.btnSetRange);

        switch (view.getId()) {
            case R.id.btnSetSearchRange :
                alertUser("지도를 클릭해 범위를 지정해주세요.(범위 탐색)");
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                mNaverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                        createSearchRange(latLng);
                    }
                });
                setSearchRangeButton.setVisibility(View.GONE);
                setPolygonRangeButton.setVisibility(View.GONE);
                missionCancelButton.setVisibility(View.GONE);
                setRangeValue.setText(R.string.mission_set_ab_position);
                break;

            case R.id.btnSetPolygonRange :
                alertUser("지도를 클릭해 범위를 지정해주세요.(다각형)");
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                mNaverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                        createPolygonRange(latLng);
                    }
                });
                setSearchRangeButton.setVisibility(View.GONE);
                setPolygonRangeButton.setVisibility(View.GONE);
                missionCancelButton.setVisibility(View.GONE);
                setRangeValue.setText(R.string.mission_set_polygon_position);
                break;

            case R.id.btnMissionCancel :
                alertUser("범위 설정을 취소합니다.");
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                mNaverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                        alertUserError("범위설정모드를 선택해주세요.");
                    }
                });

                setSearchRangeButton.setVisibility(View.GONE);
                setPolygonRangeButton.setVisibility(View.GONE);
                missionCancelButton.setVisibility(View.GONE);
                setRangeValue.setText(R.string.mission_open_set_range);
                sendMissionButton.setVisibility(View.INVISIBLE);

                mSearchPolylineOverlay.setMap(null);
                mSearchPolygonOverlay.setMap(null);

                if (mSearchMarker.size() != 0) {
                    for (int i = 0; i < mSearchMarker.size(); i++) {
                        mSearchMarker.get(i).setMap(null);
                    }
                }

                mSearchMarker.clear();
                mPolygonMarker.clear();
                mSearchPolyline.clear();
                mSearchPolygon.clear();

                mSearchMarkerCount = 0;
                mGapCount = 0;
                mReachedCount = 1;
                break;
        }
    }

    // UI Updating //

    public void updateDroneLocation() {
        mGps = this.drone.getAttribute(AttributeType.GPS);

        LatLng droneLocation = new LatLng(mGps.getPosition().getLatitude(), mGps.getPosition().getLongitude());
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(droneLocation).animate(CameraAnimation.Linear);

        if (mMapLock == true) {
            droneMarker.setPosition(droneLocation);
            droneMarker.setIcon(OverlayImage.fromResource(R.drawable.location_overlay_icon));
            droneMarker.setFlat(true);
            droneMarker.setWidth(80);
            droneMarker.setHeight(80);
            droneMarker.setMap(mNaverMap);
            droneMarker.setAnchor(new PointF(0.5f, 0.5f));
            droneMarker.setAngle(mYaw);
            mNaverMap.moveCamera(cameraUpdate);

            poly.add(0, droneLocation);
            polylineOverlay.setCoords(poly);
            poly.set(0, droneLocation);
            polylineOverlay.setCoords(poly);
            polylineOverlay.setWidth(4);
            polylineOverlay.setCapType(PolylineOverlay.LineCap.Round);
            polylineOverlay.setJoinType(PolylineOverlay.LineJoin.Round);
            polylineOverlay.setColor(Color.RED);
            polylineOverlay.setMap(mNaverMap);
        } else {
            droneMarker.setPosition(droneLocation);
            droneMarker.setIcon(OverlayImage.fromResource(R.drawable.location_overlay_icon));
            droneMarker.setFlat(true);
            droneMarker.setWidth(80);
            droneMarker.setHeight(80);
            droneMarker.setMap(mNaverMap);
            droneMarker.setAnchor(new PointF(0.5f, 0.5f));
            droneMarker.setAngle(mYaw);

            poly.add(0, droneLocation);
            polylineOverlay.setCoords(poly);
            poly.set(0, droneLocation);
            polylineOverlay.setCoords(poly);
            polylineOverlay.setWidth(4);
            polylineOverlay.setCapType(PolylineOverlay.LineCap.Round);
            polylineOverlay.setJoinType(PolylineOverlay.LineJoin.Round);
            polylineOverlay.setColor(Color.RED);
            polylineOverlay.setMap(mNaverMap);
        }

    }

    protected void updateUI(Boolean isConnected) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

        LinearLayout layoutARM = findViewById(R.id.layoutARM);
        LinearLayout layoutDroneAttribute = findViewById(R.id.layoutDroneAttribute);

        TextView altitudeTextView = (TextView) findViewById(R.id.btnTakeOffAltitude);
        TextView abDistanceTextView = (TextView) findViewById(R.id.btnABDistance);
        TextView flightWidthTextView = (TextView) findViewById(R.id.btnFlightWidth);

        if (isConnected) {
            connectButton.setText("Disconnect");
            layoutARM.setVisibility(View.VISIBLE);
            layoutDroneAttribute.setVisibility(View.VISIBLE);
            altitudeTextView.setText(String.format("%2.1fm\n이륙고도", mDroneAltitude));
            abDistanceTextView.setText(String.format("%dm\n비행 거리", mABDistance));
            flightWidthTextView.setText(String.format("%2.1fm\n비행폭", mFlightWidth));
        } else {
            connectButton.setText("Connect");
            layoutARM.setVisibility(View.INVISIBLE);
            layoutDroneAttribute.setVisibility(View.INVISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    protected void clearValue() {
        Button sendMissionButton = (Button) findViewById(R.id.btnSendMission);
        sendMissionButton.setVisibility(View.INVISIBLE);

        TextView sendMissionValue = (TextView) findViewById(R.id.btnSendMission);
        sendMissionValue.setText(R.string.mission_send);

        TextView voltageTextView = (TextView) findViewById(R.id.voltageValueTextView);
        voltageTextView.setText(String.format("0V")); // Clear voltage
        voltageTextView.setTextColor(Color.WHITE);

        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        altitudeTextView.setText(String.format("0m")); // Clear altitude

        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        speedTextView.setText(String.format("0m/s")); // Clear speed

        TextView yawTextView = (TextView) findViewById(R.id.YAWValueTextView);
        yawTextView.setText(String.format("0deg")); // Clear yaw

        TextView gpsTextView = (TextView) findViewById(R.id.gpsValueTextView);
        gpsTextView.setText(String.format("0")); // Clear GPS count
        gpsTextView.setTextColor(Color.WHITE);

        droneMarker.setMap(null); // Clear drone marker
        polylineOverlay.setMap(null); // Clear path

        poly.removeAll(poly);
        polylineOverlay.setMap(null);
        mSearchPolylineOverlay.setMap(null);
        mSearchPolygonOverlay.setMap(null);

        if (mSearchMarker.size() != 0) {
            for (int i = 0; i < mSearchMarker.size(); i++) {
                mSearchMarker.get(i).setMap(null);
            }
        }

        poly.clear();
        mSearchMarker.clear();
        mPolygonMarker.clear();
        mSearchPolyline.clear();
        mSearchPolygon.clear();

        mSearchMarkerCount = 0;
        mGapCount = 0;
        mReachedCount = 1;
    }

    protected void updateVoltage() { // Drone battery value
        TextView voltageTextView = findViewById(R.id.voltageValueTextView);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        voltageTextView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");

        if (droneBattery.getBatteryVoltage() < 11) {
            voltageTextView.setTextColor(Color.RED);
        } else {
            voltageTextView.setTextColor(Color.WHITE);
        }
    }

    protected void updateAltitude() { // Drone altitude value
        TextView altitudeTextView = findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");

        if (droneAltitude.getAltitude() < 0) {
            altitudeTextView.setTextColor(Color.RED);
        } else {
            altitudeTextView.setTextColor(Color.WHITE);
        }
    }

    protected void updateSpeed() { // Drone speed value
        TextView speedTextView = findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateYaw() { // Yaw value
        TextView yawTextView = findViewById(R.id.YAWValueTextView);
        mDroneYaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        mYaw = (float) mDroneYaw.getYaw();
        if (mYaw < 0) {
            mYaw = mYaw + 360;
        } else {
            mYaw = (float) mDroneYaw.getYaw();
        }
        yawTextView.setText(String.format("%3.0f", mYaw) + "deg");
    }

    protected void updateSatellitesCount() { // Satellite Count
        TextView gpsTextView = findViewById(R.id.gpsValueTextView);
        Gps droneGpsCount = this.drone.getAttribute(AttributeType.GPS);
        gpsTextView.setText(String.format("%d", droneGpsCount.getSatellitesCount()));

        if (droneGpsCount.getSatellitesCount() < 10) {
            gpsTextView.setTextColor(Color.RED);
        } else {
            gpsTextView.setTextColor(Color.WHITE);
        }
    }

    protected void updateVehicleModesForType(int droneType) { // Drone Mode
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() { // Drone Mode
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    // Helper methods //

    protected void alertUser(String message) {
        // 기본 로그 //
        mDroneDataLog.add(" ☆ " + message);
        mDroneRecyclerView.smoothScrollToPosition(mDroneDataLog.size()-1);
        mDroneLog.notifyDataSetChanged();
    }

    protected void alertUserError(String message) {
        // 오류 로그 //
        mDroneDataLog.add(" ※ " + message);
        mDroneRecyclerView.smoothScrollToPosition(mDroneDataLog.size()-1);
        mDroneLog.notifyDataSetChanged();
    }

    // FAB Button methods //

    @Override
    public void onClick(View view) {
        int id = view.getId();

        switch (id) {
            case R.id.fabMain:
                anim();
                break;
            case R.id.fabBasic:
                anim();
                alertUser("지도 타입 변경 : 기본");
                mNaverMap.setMapType(NaverMap.MapType.Basic);
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, true);
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRANSIT, true);
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BICYCLE, true);
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_MOUNTAIN, true);
                break;
            case R.id.fabNavi:
                anim();
                alertUser("지도 타입 변경 : 네비");
                mNaverMap.setMapType(NaverMap.MapType.Navi);
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, true);
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRAFFIC, true);
                break;
            case R.id.fabSatellite:
                anim();
                alertUser("지도 타입 변경 : 위성");
                mNaverMap.setMapType(NaverMap.MapType.Satellite);
                break;
            case R.id.fabHybrid:
                anim();
                alertUser("지도 타입 변경 : 하이브리드");
                mNaverMap.setMapType(NaverMap.MapType.Hybrid);
                break;
            case R.id.fabTerrain:
                anim();
                alertUser("지도 타입 변경 : 지적도");
                mNaverMap.setMapType(NaverMap.MapType.Terrain);
                break;
        }
    }

    public void anim() {
        if (openFlag) {
            mFabBasic.startAnimation(fab_close);
            mFabNavi.startAnimation(fab_close);
            mFabSatellite.startAnimation(fab_close);
            mFabHybrid.startAnimation(fab_close);
            mFabTerrain.startAnimation(fab_close);

            mFabBasic.setClickable(false);
            mFabNavi.setClickable(false);
            mFabSatellite.setClickable(false);
            mFabHybrid.setClickable(false);
            mFabTerrain.setClickable(false);

            openFlag = false;
        } else {
            mFabBasic.startAnimation(fab_open);
            mFabNavi.startAnimation(fab_open);
            mFabSatellite.startAnimation(fab_open);
            mFabHybrid.startAnimation(fab_open);
            mFabTerrain.startAnimation(fab_open);

            mFabBasic.setClickable(true);
            mFabNavi.setClickable(true);
            mFabSatellite.setClickable(true);
            mFabHybrid.setClickable(true);
            mFabTerrain.setClickable(true);

            openFlag = true;
        }
    }

    // 탐색 미션 //

    private void createSearchRange(LatLng latLng) {
        // 범위 그리기
        if (mGapCount < 2) {
            Marker marker = new Marker();
            marker.setPosition(latLng);

            mSearchPolygon.add(latLng);
            mSearchMarker.add(marker);
            mSearchMarker.get(mSearchMarkerCount).setMap(mNaverMap);

            if (mGapCount == 0) {
                mSearchMarker.get(0).setIcon(OverlayImage.fromResource(R.drawable.ic_marker_apoint));
                mSearchMarker.get(0).setWidth(50);
                mSearchMarker.get(0).setHeight(50);
                mSearchMarker.get(0).setAnchor(new PointF(0.5F, 0.5F));
            } else if (mGapCount == 1) {
                mSearchMarker.get(1).setIcon(OverlayImage.fromResource(R.drawable.ic_marker_bpoint));
                mSearchMarker.get(1).setWidth(50);
                mSearchMarker.get(1).setHeight(50);
                mSearchMarker.get(1).setAnchor(new PointF(0.5F, 0.5F));
            }

            mGapCount++;
            mSearchMarkerCount++;
        }
        if (mSearchMarkerCount == 2) {
            double heading = MyUtil.computeHeading(mSearchMarker.get(0).getPosition(), mSearchMarker.get(1).getPosition());

            LatLng latLng1 = MyUtil.computeOffset(mSearchMarker.get(1).getPosition(), mABDistance, heading + 90);
            LatLng latLng2 = MyUtil.computeOffset(mSearchMarker.get(0).getPosition(), mABDistance, heading + 90);

            mSearchPolygon.add(latLng1);
            mSearchPolygon.add(latLng2);
            mSearchPolygonOverlay.setCoords(mSearchPolygon);

            int colorLightGreen = getResources().getColor(R.color.colorLightGreen);

            mSearchPolygonOverlay.setColor(colorLightGreen);
            mSearchPolygonOverlay.setMap(mNaverMap);

            createSearchPath();
        }
    }

    private void createSearchPath() {
        // 경로 생성
        double heading = MyUtil.computeHeading(mSearchMarker.get(0).getPosition(), mSearchMarker.get(1).getPosition());

        mSearchPolyline.add(new LatLng(mSearchMarker.get(0).getPosition().latitude, mSearchMarker.get(0).getPosition().longitude));
        mSearchPolyline.add(new LatLng(mSearchMarker.get(1).getPosition().latitude, mSearchMarker.get(1).getPosition().longitude));

        for (double sum = mFlightWidth; sum + mFlightWidth <= mABDistance + mFlightWidth; sum = sum + mFlightWidth) {
            LatLng latLng1 = MyUtil.computeOffset(mSearchMarker.get(mSearchMarkerCount - 1).getPosition(), mFlightWidth, heading + 90);
            LatLng latLng2 = MyUtil.computeOffset(mSearchMarker.get(mSearchMarkerCount - 2).getPosition(), mFlightWidth, heading + 90);

            mSearchMarker.add(new Marker(latLng1));
            mSearchMarker.add(new Marker(latLng2));
            mSearchMarkerCount += 2;

            mSearchPolyline.add(latLng1);
            mSearchPolyline.add(latLng2);
        }

        mSearchPolylineOverlay.setColor(Color.WHITE);
        mSearchPolylineOverlay.setCoords(mSearchPolyline);
        mSearchPolylineOverlay.setMap(mNaverMap);
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        mNaverMap.setOnMapClickListener(null);

        createMission();
    }

    private void createMission() {
        // 미션 입력
        final Mission mMission = new Mission();

        for (int i = 0; i < mSearchPolyline.size(); i++) {
            Waypoint waypoint = new Waypoint();
            waypoint.setDelay(1);

            LatLongAlt latLongAlt = new LatLongAlt(mSearchPolyline.get(i).latitude, mSearchPolyline.get(i).longitude, mDroneAltitude);
            waypoint.setCoordinate(latLongAlt);

            mMission.addMissionItem(waypoint);
        }

        final Button sendMissionButton = (Button) findViewById(R.id.btnSendMission);
        final Button setRangeButton = (Button) findViewById(R.id.btnSetRange);
        sendMissionButton.setVisibility(View.VISIBLE);


        sendMissionButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (setRangeButton.getText().equals("AB\n좌표설정")) {
                    if (sendMissionButton.getText().equals("임무 전송")) {
                        if (mSearchPolygon.size() == 4) {
                            setMission(mMission);
                            alertUser("임무 전송");
                        } else {
                            alertUserError("A,B좌표 필요");
                        }
                    } else if (sendMissionButton.getText().equals("임무 시작")) {
                        // Auto모드로 전환
                        ChangeToAutoMode();
                        sendMissionButton.setText("임무 중지");
                        alertUser("임무 시작");
                    } else if (sendMissionButton.getText().equals("임무 중지")) {
                        pauseMission();
                        ChangeToLoiterMode();
                        sendMissionButton.setText("임무 재시작");
                        alertUser("임무 중지");
                    } else if (sendMissionButton.getText().equals("임무 재시작")) {
                        ChangeToAutoMode();
                        sendMissionButton.setText("임무 중지");
                        alertUser("임무 재시작");
                    }
                } else if (setRangeButton.getText().equals("다각형\n좌표설정")) {
                    if (sendMissionButton.getText().equals("임무 전송")) {
                        if (mSearchPolygon.size() >= 3) {
                            setMission(mMission);
                            alertUser("임무 전송");
                        } else {
                            alertUserError("3개이상의 좌표를 선택해주세요.");
                        }
                    } else if (sendMissionButton.getText().equals("임무 시작")) {
                        // Auto모드로 전환
                        ChangeToAutoMode();
                        sendMissionButton.setText("임무 중지");
                        alertUser("임무 시작");
                    } else if (sendMissionButton.getText().equals("임무 중지")) {
                        pauseMission();
                        ChangeToLoiterMode();
                        sendMissionButton.setText("임무 재시작");
                        alertUser("임무 중지");
                    } else if (sendMissionButton.getText().equals("임무 재시작")) {
                        ChangeToAutoMode();
                        sendMissionButton.setText("임무 중지");
                        alertUser("임무 재시작");
                    }
                }
            }
        });
    }

    private void setMission(Mission mMission) {
        MissionApi.getApi(this.drone).setMission(mMission, true);
    }

    private void pauseMission() {
        MissionApi.getApi(this.drone).pauseMission(null);
    }

    private void sendMission() {
        alertUser("미션 업로드 완료");
        Button BtnSendMission = (Button) findViewById(R.id.btnSendMission);
        BtnSendMission.setText("임무 시작");
    }

    // 다각형 그리기 //

    private void createPolygonRange(LatLng latLng) {
        mSearchPolygon.add(latLng);

        Marker marker = new Marker();
        marker.setPosition(latLng);
        mSearchMarker.add(marker);


        mSearchMarker.get(mSearchMarkerCount).setAnchor(new PointF(0.5F,1.0F));

        mSearchMarker.get(mSearchMarkerCount).setMap(mNaverMap);

        mSearchMarkerCount++;

        mNaverMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                if (mSearchPolygon.size() >= 2) {
                    mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                    mSearchPolygonOverlay.setCoords(mSearchPolygon);

                    int colorLightBlue = getResources().getColor(R.color.colorLightGreen);

                    mSearchPolygonOverlay.setColor(colorLightBlue);
                    mSearchPolygonOverlay.setMap(mNaverMap);

                    createPolygonPath();

                    mNaverMap.setOnMapClickListener(null);
                    mNaverMap.setOnMapLongClickListener(null);
                } else {
                    alertUserError("3개이상의 좌표를 선택해주세요.");
                }
            }
        });
    }

    private void createPolygonPath() {
        int i = 2;
        boolean switchFlag = true;
        mSearchPolyline.add(new LatLng(mSearchMarker.get(0).getPosition().latitude, mSearchMarker.get(0).getPosition().longitude));
        mSearchPolyline.add(new LatLng(mSearchMarker.get(1).getPosition().latitude, mSearchMarker.get(1).getPosition().longitude));
        mPolygonMarker.add(mSearchMarker.get(0));
        mPolygonMarker.add(mSearchMarker.get(1));

        for (double sum = mFlightWidth; sum + mFlightWidth <= mABDistance + mFlightWidth; sum = sum + mFlightWidth) {
            double heading1 = MyUtil.computeHeading(mSearchMarker.get(1).getPosition(), mSearchMarker.get(2).getPosition());
            double heading2 = MyUtil.computeHeading(mSearchMarker.get(0).getPosition(), mSearchMarker.get(mSearchMarkerCount - 1).getPosition());

            if (switchFlag == true) {
                LatLng latLng1 = MyUtil.computeOffset(mPolygonMarker.get(i - 1).getPosition(), mFlightWidth, heading1);
                LatLng latLng2 = MyUtil.computeOffset(mPolygonMarker.get(i - 2).getPosition(), mFlightWidth, heading2);

                mPolygonMarker.add(new Marker(latLng1));
                mPolygonMarker.add(new Marker(latLng2));
                i += 2;

                mSearchPolyline.add(latLng1);
                mSearchPolyline.add(latLng2);

                switchFlag = false;
            } else {
                LatLng latLng1 = MyUtil.computeOffset(mPolygonMarker.get(i - 1).getPosition(), mFlightWidth, heading2);
                LatLng latLng2 = MyUtil.computeOffset(mPolygonMarker.get(i - 2).getPosition(), mFlightWidth, heading1);

                mPolygonMarker.add(new Marker(latLng1));
                mPolygonMarker.add(new Marker(latLng2));
                i += 2;

                mSearchPolyline.add(latLng1);
                mSearchPolyline.add(latLng2);

                switchFlag = true;
            }
        }
        mSearchPolylineOverlay.setColor(Color.WHITE);
        mSearchPolylineOverlay.setCoords(mSearchPolyline);
        mSearchPolylineOverlay.setMap(mNaverMap);
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);

        createMission();
    }

    // 비행모드 변경 //

    private void ChangeToLoiterMode() {
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Loiter 모드로 변경 중...");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Loiter 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Loiter 모드 변경 실패");
            }
        });
    }

    private void ChangeToAutoMode() {
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Auto 모드로 변경 중...");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Auto 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Auto 모드 변경 실패.");
            }
        });
    }
}
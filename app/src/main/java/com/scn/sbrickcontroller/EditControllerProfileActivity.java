package com.scn.sbrickcontroller;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.scn.sbrickcontrollerprofilemanager.SBrickControllerProfile;
import com.scn.sbrickcontrollerprofilemanager.SBrickControllerProfileManagerHolder;
import com.scn.sbrickmanager.SBrick;
import com.scn.sbrickmanager.SBrickManagerHolder;

import java.util.ArrayList;
import java.util.List;

public class EditControllerProfileActivity extends BaseActivity {

    //
    // Private members
    //

    private static final String TAG = EditControllerProfileActivity.class.getSimpleName();

    private ListView lwControllerActions;
    private ControllerActionListAdapter conrollerActionListAdapter;

    int profileIndex;
    SBrickControllerProfile profile;

    //
    // Activity overrides
    //

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate...");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_controller_profile);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        profileIndex = intent.getIntExtra(Constants.EXTRA_CONTROLLER_PROFILE_INDEX, 0);
        profile = intent.getParcelableExtra(Constants.EXTRA_CONTROLLER_PROFILE);

        lwControllerActions = (ListView)findViewById(R.id.listview_conroller_actions);
        lwControllerActions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "onItemClick...");

                if (position == 0) {
                    Log.i(TAG, "  Click on head item.");
                    return;
                }

                int profileIndex = SBrickControllerProfileManagerHolder.getManager().getProfiles().indexOf(profile);
                String controllerActionId = ControllerActionListAdapter.getControllerActionId(position - 1);
                List<String> sbrickAddresses = SBrickManagerHolder.getManager().getSBrickAddresses();

                Intent intent = new Intent(EditControllerProfileActivity.this, EditControllerActionActivity.class);
                intent.putExtra(Constants.EXTRA_CONTROLLER_PROFILE_INDEX, profileIndex);
                intent.putExtra(Constants.EXTRA_CONTROLLER_ACTION_ID, controllerActionId);
                intent.putStringArrayListExtra(Constants.EXTRA_SBRICK_ADDRESS_LIST, new ArrayList(sbrickAddresses));
                startActivity(intent);
            }
        });
        conrollerActionListAdapter = new ControllerActionListAdapter(this, profile);
        lwControllerActions.setAdapter(conrollerActionListAdapter);
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume...");

        super.onResume();
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause...");

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu...");

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_edit_controller_profile, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "onOptionsItemSelected...");
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {

            case R.id.menu_item_done:
                Log.i(TAG, "  menu_item_done");

                // TODO: save changes on the profile

                return true;
        }

        return false;
    }

    //
    // Private methods and classes
    //

    private static class ControllerActionListAdapter extends BaseAdapter {

        //
        // Private members
        //

        private static final int ViewTypeProfileName = 0;
        private static final int ViewTypeControllerAction = 1;

        private Context context;
        private SBrickControllerProfile profile;

        public ControllerActionListAdapter(Context context, SBrickControllerProfile profile) {
            this.context = context;
            this.profile = profile;
        }

        //
        // BaseAdapter overrides
        //


        @Override
        public int getViewTypeCount() {
            // profile name and controller actions
            return 2;
        }

        @Override
        public int getCount() {
            // profile name + 18 controller action
            return 19;
        }

        @Override
        public int getItemViewType(int position) {
            // profile name: 0
            // controller actions: 1
            return position == 0 ? ViewTypeProfileName : ViewTypeControllerAction;
        }

        @Override
        public Object getItem(int position) {
            if (position == 0) {
                // Profile name
                return profile.getName();
            }
            else {
                // Controller action
                String controllerActionId = getControllerActionId(position - 1);
                SBrickControllerProfile.ControllerAction controllerAction = profile.getControllerAction(controllerActionId);
                return controllerAction;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            switch (getItemViewType(position)) {

                case ViewTypeProfileName:

                    if (rowView == null) {
                        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        rowView = inflater.inflate(R.layout.listview_item_controller_action_head, parent, false);
                    }

                    EditText etProfileName = (EditText)rowView.findViewById(R.id.edittext_controller_profile_name);
                    etProfileName.setText(profile.getName());

                    break;

                case ViewTypeControllerAction:

                    if (rowView == null) {
                        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        rowView = inflater.inflate(R.layout.listview_item_controller_action, parent, false);
                    }

                    final String controllerActionName = SBrickControllerProfile.getControllerActionName(getControllerActionId(position - 1));
                    final SBrickControllerProfile.ControllerAction controllerAction = (SBrickControllerProfile.ControllerAction)getItem(position);

                    TextView twControllerActionName = (TextView) rowView.findViewById(R.id.textview_controller_action_name);
                    TextView twSBrickName = (TextView) rowView.findViewById(R.id.textview_sbrick_name);
                    TextView twChannel = (TextView) rowView.findViewById(R.id.textview_channel);
                    TextView twInvert = (TextView) rowView.findViewById(R.id.textview_invert);

                    if (controllerAction != null) {
                        String sbrickAddress = controllerAction.getSbrickAddress();
                        SBrick sbrick = SBrickManagerHolder.getManager().getSBrick(sbrickAddress);

                        twControllerActionName.setText(controllerActionName);
                        twSBrickName.setText(sbrick != null ? sbrick.getName() : "?????");
                        twChannel.setText(Integer.toString(controllerAction.getChannel() + 1));
                        twInvert.setText(controllerAction.getInvert() ? "Invert" : "Non-invert");
                    }
                    else {
                        twControllerActionName.setText(controllerActionName);
                        twSBrickName.setText("-");
                        twChannel.setText("-");
                        twInvert.setText("-");
                    }

                    Button btnDeleteControllerAction = (Button) rowView.findViewById(R.id.button_delete_controller_action);
                    btnDeleteControllerAction.setVisibility(controllerAction != null ? View.VISIBLE : View.INVISIBLE);
                    btnDeleteControllerAction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Helper.showQuestionDialog(
                                    context,
                                    "Do you really want to delete this action?",
                                    "Yes",
                                    "No",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Log.i(TAG, "onClick...");
                                            profile.removeControllerAction(getControllerActionId(position));
                                            ControllerActionListAdapter.this.notifyDataSetChanged();
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Do nothing here
                                        }
                                    });
                        }
                    });

                    break;

            }

            return rowView;
        }

        //
        // Private methods
        //

        private static String getControllerActionId(int position) {
            switch (position) {
                case 0: return SBrickControllerProfile.CONTROLLER_ACTION_DPAD_LEFT_RIGHT;
                case 1: return SBrickControllerProfile.CONTROLLER_ACTION_DPAD_UP_DOWN;
                case 2: return SBrickControllerProfile.CONTROLLER_ACTION_AXIS_X;
                case 3: return SBrickControllerProfile.CONTROLLER_ACTION_AXIS_Y;
                case 4: return SBrickControllerProfile.CONTROLLER_ACTION_THUMB_L;
                case 5: return SBrickControllerProfile.CONTROLLER_ACTION_AXIS_Z;
                case 6: return SBrickControllerProfile.CONTROLLER_ACTION_AXIS_RZ;
                case 7: return SBrickControllerProfile.CONTROLLER_ACTION_THUMB_R;
                case 8: return SBrickControllerProfile.CONTROLLER_ACTION_A;
                case 9: return SBrickControllerProfile.CONTROLLER_ACTION_B;
                case 10: return SBrickControllerProfile.CONTROLLER_ACTION_X;
                case 11: return SBrickControllerProfile.CONTROLLER_ACTION_Y;
                case 12: return SBrickControllerProfile.CONTROLLER_ACTION_R1;
                case 13: return SBrickControllerProfile.CONTROLLER_ACTION_R_TRIGGER;
                case 14: return SBrickControllerProfile.CONTROLLER_ACTION_L1;
                case 15: return SBrickControllerProfile.CONTROLLER_ACTION_L_TRIGGER;
                case 16: return SBrickControllerProfile.CONTROLLER_ACTION_START;
                case 17: return SBrickControllerProfile.CONTROLLER_ACTION_SELECT;
            }
            return "";
        }
    }
}

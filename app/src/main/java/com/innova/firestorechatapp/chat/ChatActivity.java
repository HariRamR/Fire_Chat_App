package com.innova.firestorechatapp.chat;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.innova.firestorechatapp.R;
import com.innova.firestorechatapp.fragment.ChatFragment;
import com.innova.firestorechatapp.fragment.UserListInRoomFragment;

public class ChatActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private ChatFragment chatFragment;
    private UserListInRoomFragment userListInRoomFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Toolbar toolbar = findViewById(R.id.toolbar);
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        ImageView toolbarIcon = findViewById(R.id.toolbar_icon);

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbarIcon.setImageDrawable(getResources().getDrawable(R.drawable.chat_icon));

        setSupportActionBar(toolbar);

        String toUid = getIntent().getStringExtra("toUid");
        final String roomID = getIntent().getStringExtra("roomID");
        String roomTitle = getIntent().getStringExtra("roomTitle");
        int userCountInRoom = getIntent().getIntExtra("userCountInRoom", 0);
        if (roomTitle!=null) {
//            actionBar.setTitle(roomTitle);
            toolbarTitle.setText(roomTitle);
        } else toolbarTitle.setText("Chat Messages");

        // left drawer
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        /*if (userCountInRoom > 2){ enable adding users in grp

            findViewById(R.id.rightMenuBtn).setVisibility(View.VISIBLE);
        }else findViewById(R.id.rightMenuBtn).setVisibility(View.GONE);*/

        findViewById(R.id.rightMenuBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drawerLayout.isDrawerOpen(Gravity.RIGHT)) {
                    drawerLayout.closeDrawer(Gravity.RIGHT);
                } else {
                    if (userListInRoomFragment==null) {
                        userListInRoomFragment = UserListInRoomFragment.getInstance(roomID, chatFragment.getUserList());
                        getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.drawerFragment, userListInRoomFragment)
                                .commit();
                    }
                    drawerLayout.openDrawer(Gravity.RIGHT);
                }
            }
        });
        // chatting area
        chatFragment = ChatFragment.getInstance(toUid, roomID);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mainFragment, chatFragment )
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        chatFragment.backPressed();
        finish();
    }
}

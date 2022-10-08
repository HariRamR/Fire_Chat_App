package com.innova.firestorechatapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.innova.firestorechatapp.chat.ChatActivity;
import com.innova.firestorechatapp.chat.SelectUserActivity;
import com.innova.firestorechatapp.common.Util9;
import com.innova.firestorechatapp.model.ChatRoomModel;
import com.innova.firestorechatapp.model.Message;
import com.innova.firestorechatapp.model.UserModel;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.Consumer;

import static android.view.View.INVISIBLE;

public class MainActivity extends AppCompatActivity {

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private RecyclerViewAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        ImageView toolbarIcon = findViewById(R.id.toolbar_icon);
        toolbar.setNavigationIcon(getResources().getDrawable(R.drawable.ic_ham_24));
        setSupportActionBar(toolbar);

        toolbarTitle.setText(R.string.chat_messages);
        toolbarIcon.setImageDrawable(getResources().getDrawable(R.drawable.chat_icon));

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout_dashboard);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        toggle.setDrawerIndicatorEnabled(false);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(mAdapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);

        FloatingActionButton makeRoomBtn = findViewById(R.id.makeRoomBtn);
        makeRoomBtn.setVisibility(INVISIBLE);
        makeRoomBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), SelectUserActivity.class));
            }
        });

        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

        CheckChatRooms();
        getUserInfoFromServer();
    }

    public void CheckChatRooms() {

        SharedPreferences pref = getSharedPreferences("Pref", MODE_PRIVATE);
        final String sectionName, className, sectionId;
        className = pref.getString("ClassName", null);
        sectionName = pref.getString("SectionName", null);
        sectionId = String.valueOf(pref.getInt("sectionId", -1));
        final String currentUserUID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        final HashMap<String, String> roomIdMap = new HashMap<>();
        final HashMap<String, Set<String>> roomUserIdMap = new HashMap<>();

        if (className != null && sectionName != null && !sectionId.equals("-1")){

            FirebaseFirestore fireStore = FirebaseFirestore.getInstance();

            // all room information
           fireStore.collection("rooms").get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
               @Override
               public void onSuccess(QuerySnapshot queryDocumentSnapshots) {

                   if (queryDocumentSnapshots == null) {
                       return;
                   }

                   for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {

                       String roomUID = doc.getData().get("roomUid") != null ? doc.getData().get("roomUid").toString() : ""; // roomUID is rooms->docId->uid
                       roomIdMap.put(roomUID, doc.getId());

                       Map<String, Long> users = (Map<String, Long>) doc.get("users");

                       Set<String> usersSet = new HashSet<>(users.keySet()); // key is rooms->docId->users->individual id (user id)
                       roomUserIdMap.put(roomUID, usersSet);
                   }

                   boolean isRoomAvailable = false;
                   for (final String roomUid : roomIdMap.keySet()) {

                       if (roomUid.equals(sectionId)) {

                           isRoomAvailable = true;

                           // get that particular room document and check if current userid is already added or not
                           Set<String> usersSet = roomUserIdMap.get(roomUid);
                           if (!usersSet.contains(currentUserUID)) {

                               // User is not added in the room so add
                               Map<String, Integer> users = new HashMap<>();

                               for (String user: usersSet) {

                                   users.put(user, 0);
                               }
                               users.put(currentUserUID, 0);
                               Map<String, Object> data = new HashMap<>();
                               data.put("roomUid", sectionId);
                               data.put("title", className + " " + sectionName);
                               data.put("users", users);

                               DocumentReference room = FirebaseFirestore.getInstance().collection("rooms").document(roomIdMap.get(roomUid));
                               room.set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
                                   @Override
                                   public void onComplete(@NonNull Task<Void> task) {
                                       if (task.isSuccessful()) {

                                           // need to check rooms will automatically load
                                           Toast.makeText(MainActivity.this, "user added to room " + roomUid + " successfully", Toast.LENGTH_SHORT).show();
                                       }
                                   }
                               });
                           }
                           break;
                       }
                   }
                   if (!isRoomAvailable){

                       Map<String, Integer> users = new HashMap<>();
                       users.put(currentUserUID, 0);
                       Map<String, Object> data = new HashMap<>();
                       data.put("roomUid", sectionId);
                       data.put("title", className + " " + sectionName);
                       data.put("users", users);

                       DocumentReference room = FirebaseFirestore.getInstance().collection("rooms").document();
                       room.set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
                           @Override
                           public void onComplete(@NonNull Task<Void> task) {
                               if (task.isSuccessful()) {

                                   // need to check rooms will automatically load
                                   Toast.makeText(MainActivity.this, "room created successfully", Toast.LENGTH_SHORT).show();
//                                   mAdapter.notifyDataSetChanged();
                               }
                           }
                       });
                   }
               }
           });
        }
    }

    void getUserInfoFromServer(){

        final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        final FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference docRef = FirebaseFirestore.getInstance().collection("users").document(uid);
        docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                UserModel userModel = documentSnapshot.toObject(UserModel.class);

                if (userModel.getUid() == null) { // if condition added by hari

                    String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                    String token = userModel.getToken();
                    userModel = new UserModel();
                    userModel.setToken(token);
                    userModel.setUid(uid);
                    userModel.setUserid(email);
                    userModel.setUsermsg("...");

                    db.collection("users").document(uid)
                            .set(userModel);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {

            mAdapter.stopListening();
        }
    }

    // =============================================================================================
    class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));
        private List<ChatRoomModel> roomList = new ArrayList<>();
        //        private Map<String, UserModel> userList = new HashMap<>();
        private String myUid;
        private StorageReference storageReference;
        private FirebaseFirestore firestore;
        private ListenerRegistration listenerRegistration;

        RecyclerViewAdapter() {

            firestore = FirebaseFirestore.getInstance();
            storageReference = FirebaseStorage.getInstance().getReference();
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            getRoomInfo();
        }

        Integer unreadTotal = 0;

        public void getRoomInfo() {

            // my chatting room information
            listenerRegistration = firestore.collection("rooms").whereGreaterThanOrEqualTo("users." + myUid, 0)
//                    a.orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException e) {
                            if (e != null) {
                                return;
                            }

                            TreeMap<Date, ChatRoomModel> orderedRooms = new TreeMap<Date, ChatRoomModel>(Collections.reverseOrder());

                            for (final QueryDocumentSnapshot document : value) {

                                Message message = document.toObject(Message.class);
                                if (message.getMsg() != null & message.getTimestamp() == null) {
                                    continue;
                                } // FieldValue.serverTimestamp is so late

                                ChatRoomModel chatRoomModel = new ChatRoomModel();
                                chatRoomModel.setRoomID(document.getId());

                                if (message.getMsg() != null) { // there are no last message
                                    chatRoomModel.setLastDatetime(simpleDateFormat.format(message.getTimestamp()));
                                    switch (message.getMsgtype()) {
                                        case "1":
                                            chatRoomModel.setLastMsg("Image");
                                            break;
                                        case "2":
                                            chatRoomModel.setLastMsg("File");
                                            break;
                                        default:
                                            chatRoomModel.setLastMsg(message.getMsg());
                                    }
                                }

                                Map<String, Long> users = (Map<String, Long>) document.get("users");
                                chatRoomModel.setUserCount(users.size());
                                for (String key : users.keySet()) {
                                    if (myUid.equals(key)) {

                                        int unread = (int) (long) users.get(key);
                                        unreadTotal += unread;
                                        chatRoomModel.setUnreadCount(unread);
                                        break;
                                    }
                                }

                                chatRoomModel.setTitle(document.getString("title"));

                                if (message.getTimestamp() == null)
                                    message.setTimestamp(new Date());

                                if (chatRoomModel.getUserCount() > 2) {

                                    orderedRooms.put(message.getTimestamp(), chatRoomModel);
                                }
                            }

                            roomList.clear();
                            for (Map.Entry<Date, ChatRoomModel> entry : orderedRooms.entrySet()) {

                                roomList.add(entry.getValue());
                            }
                            notifyDataSetChanged();
                            setBadge(MainActivity.this, unreadTotal);
                        }
                    });
        }

        public void stopListening() {

            if (listenerRegistration != null) {
                listenerRegistration.remove();
                listenerRegistration = null;
            }

            roomList.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chatroom, parent, false);
            return new RecyclerViewAdapter.RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final RecyclerViewAdapter.RoomViewHolder roomViewHolder = (RecyclerViewAdapter.RoomViewHolder) holder;

            final ChatRoomModel chatRoomModel = roomList.get(position);

            roomViewHolder.room_title.setText(chatRoomModel.getTitle());
            roomViewHolder.last_msg.setText(chatRoomModel.getLastMsg());
            roomViewHolder.last_time.setText(chatRoomModel.getLastDatetime());

            if (chatRoomModel.getPhoto() == null) {
                Glide.with(MainActivity.this).load(R.drawable.profile_img).apply(requestOptions).into(roomViewHolder.room_image);
            } else {

                StorageReference photoStorageReference = storageReference.child("userPhoto/" + chatRoomModel.getPhoto());
                photoStorageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {

                        Glide.with(MainActivity.this).load(uri).apply(requestOptions).into(roomViewHolder.room_image);
                    }
                });
            }
            if (chatRoomModel.getUserCount() > 2) {
                roomViewHolder.room_count.setText(chatRoomModel.getUserCount().toString());
                roomViewHolder.room_count.setVisibility(View.VISIBLE);
            } else {
                roomViewHolder.room_count.setVisibility(INVISIBLE);
            }
            if (chatRoomModel.getUnreadCount() > 0) {
                roomViewHolder.unread_count.setText(chatRoomModel.getUnreadCount().toString());
                roomViewHolder.unread_count.setVisibility(View.VISIBLE);
            } else {
                roomViewHolder.unread_count.setVisibility(INVISIBLE);
            }

            roomViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), ChatActivity.class);
                    intent.putExtra("roomID", chatRoomModel.getRoomID());
                    intent.putExtra("roomTitle", chatRoomModel.getTitle());
                    intent.putExtra("userCountInRoom", chatRoomModel.getUserCount());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return roomList.size();
        }

        private class RoomViewHolder extends RecyclerView.ViewHolder {
            public ImageView room_image;
            public TextView room_title;
            public TextView last_msg;
            public TextView last_time;
            public TextView room_count;
            public TextView unread_count;

            RoomViewHolder(View view) {
                super(view);
                room_image = view.findViewById(R.id.room_image);
                room_title = view.findViewById(R.id.room_title);
                last_msg = view.findViewById(R.id.last_msg);
                last_time = view.findViewById(R.id.last_time);
                room_count = view.findViewById(R.id.room_count);
                unread_count = view.findViewById(R.id.unread_count);
            }
        }
    }

    public static void setBadge(Context context, int count) {
        String launcherClassName = getLauncherClassName(context);
        if (launcherClassName == null) {
            return;
        }
        Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
        intent.putExtra("badge_count", count);
        intent.putExtra("badge_count_package_name", context.getPackageName());
        intent.putExtra("badge_count_class_name", launcherClassName);
        context.sendBroadcast(intent);
    }

    public static String getLauncherClassName(Context context) {

        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : resolveInfos) {
            String pkgName = resolveInfo.activityInfo.applicationInfo.packageName;
            if (pkgName.equalsIgnoreCase(context.getPackageName())) {
                return resolveInfo.activityInfo.name;
            }
        }
        return null;
    }
}



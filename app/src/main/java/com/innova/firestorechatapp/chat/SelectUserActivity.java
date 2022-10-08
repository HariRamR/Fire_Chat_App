package com.innova.firestorechatapp.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.innova.firestorechatapp.R;
import com.innova.firestorechatapp.common.FirestoreAdapter;
import com.innova.firestorechatapp.common.Util9;
import com.innova.firestorechatapp.fragment.ChatFragment;
import com.innova.firestorechatapp.model.UserModel;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectUserActivity extends AppCompatActivity {
    private String roomID;
    private Map<String, String> selectedUsers = new HashMap<>();
    private FirestoreAdapter firestoreAdapter;
    private FirebaseFirestore fireStore;
    private List<String> userIdList = new ArrayList<>();

    @Override
    public void onStart() {
        super.onStart();
        if (firestoreAdapter != null) {
            firestoreAdapter.startListening();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (firestoreAdapter != null) {
            firestoreAdapter.stopListening();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_user);

        roomID = getIntent().getStringExtra("roomID");

        fireStore = FirebaseFirestore.getInstance();
        getUserDetailsInRoom();

        firestoreAdapter = new RecyclerViewAdapter(FirebaseFirestore.getInstance().collection("users").orderBy("usernm"));

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager((this)));
        recyclerView.setAdapter(firestoreAdapter);

        Button makeRoomBtn = findViewById(R.id.makeRoomBtn);
        if (roomID == null) makeRoomBtn.setOnClickListener(makeRoomClickListener);
        else makeRoomBtn.setOnClickListener(addRoomUserClickListener);
    }

    Button.OnClickListener makeRoomClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            if (selectedUsers.size() < 2) {
                Util9.showMessage(getApplicationContext(), "Please select 2 or more user");
                return;
            }

            selectedUsers.put(FirebaseAuth.getInstance().getCurrentUser().getUid(), "");

            DocumentReference newRoom = FirebaseFirestore.getInstance().collection("rooms").document();
            CreateChattingRoom(newRoom);
        }
    };

    Button.OnClickListener addRoomUserClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            if (selectedUsers.size() < 1) {
                Util9.showMessage(getApplicationContext(), "Please select 1 or more user");
                return;
            }
            CreateChattingRoom(FirebaseFirestore.getInstance().collection("rooms").document(roomID));
        }
    };

    void getUserDetailsInRoom() {

        userIdList.clear();

        if (roomID != null) {
            fireStore.collection("rooms").document(roomID).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (!task.isSuccessful()) {
                        return;
                    }
                    DocumentSnapshot document = task.getResult();
                    Map<String, Long> users = (Map<String, Long>) document.get("users");

                    for (String key : users.keySet()) {
                        getUserInfoFromServer(key);
                    }
                }
            });
        }
    }

    private void getUserInfoFromServer(String id) {

        fireStore.collection("users").document(id).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                UserModel userModel = documentSnapshot.toObject(UserModel.class);
                userIdList.add(userModel.getUid());
            }
        });
    }

    public void CreateChattingRoom(final DocumentReference room) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Map<String, Integer> users = new HashMap<>();
        String title = "";
        for (String key : selectedUsers.keySet()) {
            users.put(key, 0);
            if (title.length() < 20 & !key.equals(uid)) {
                title += selectedUsers.get(key) + ", ";
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("title", title.substring(0, title.length() - 2));
        data.put("users", users);

        room.set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Intent intent = new Intent(SelectUserActivity.this, ChatActivity.class);
                    intent.putExtra("roomID", room.getId());
                    startActivity(intent);
                    SelectUserActivity.this.finish();
                }
            }
        });
    }

    class RecyclerViewAdapter extends FirestoreAdapter<CustomViewHolder> {

        final private RequestOptions requestOptions = new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(90));
        private StorageReference storageReference;
        private String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        RecyclerViewAdapter(Query query) {
            super(query);
            storageReference = FirebaseStorage.getInstance().getReference();
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_user, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final CustomViewHolder viewHolder, int position) {
            DocumentSnapshot documentSnapshot = getSnapshot(position);
            final UserModel userModel = documentSnapshot.toObject(UserModel.class);

            if (myUid.equals(userModel.getUid()) || userIdList.contains(userModel.getUid())) {
                viewHolder.itemView.setVisibility(View.INVISIBLE);
                viewHolder.itemView.getLayoutParams().height = 0;
                return;
            }

            viewHolder.user_name.setText(userModel.getUsernm());

            if (userModel.getUserphoto() == null) {

                Glide.with(getApplicationContext()).load(R.drawable.profile_img).apply(requestOptions).into(viewHolder.user_photo);
            } else {

                StorageReference photoStorageReference = storageReference.child("userPhoto/"+userModel.getUserphoto());
                photoStorageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {

                        Glide.with(getApplicationContext())
                                .load(uri)
                                .apply(requestOptions)
                                .into(viewHolder.user_photo);
                    }
                });
            }

            viewHolder.userChk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {

                        if (userIdList.contains(userModel.getUid())) {

                            Toast.makeText(SelectUserActivity.this, userModel.getUsernm() + " is already in the group", Toast.LENGTH_SHORT).show();
                            viewHolder.userChk.setChecked(false);
                        } else selectedUsers.put(userModel.getUid(), userModel.getUsernm());
                    } else {
                        selectedUsers.remove(userModel.getUid());
                    }
                }
            });
        }
    }

    private class CustomViewHolder extends RecyclerView.ViewHolder {
        public ImageView user_photo;
        public TextView user_name;
        public CheckBox userChk;

        CustomViewHolder(View view) {
            super(view);
            user_photo = view.findViewById(R.id.user_photo);
            user_name = view.findViewById(R.id.user_name);
            userChk = view.findViewById(R.id.userChk);
        }
    }

}

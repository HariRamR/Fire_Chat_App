package com.innova.firestorechatapp.fragment;

import android.graphics.Bitmap;
import android.support.v4.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.StorageReference;
import com.innova.firestorechatapp.R;
import com.innova.firestorechatapp.UserPWActivity;
import com.innova.firestorechatapp.common.Util9;
import com.innova.firestorechatapp.interfaces.MainActivityInterface;
import com.innova.firestorechatapp.model.UserModel;

import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.io.ByteArrayOutputStream;

public class UserFragment extends Fragment {
    private static final int PICK_FROM_ALBUM = 1;
    private ImageView user_photo;
    private EditText user_id;
    private EditText user_name;
    private EditText user_msg;

    private UserModel userModel;
    private Uri userPhotoUri;
    private MainActivityInterface mainActivityInterface;

    public void initializeInterface(MainActivityInterface mainActivityInterface){

        this.mainActivityInterface = mainActivityInterface;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user, container, false);

        user_id = view.findViewById(R.id.user_id);
        user_id.setEnabled(false);
        user_name = view.findViewById(R.id.user_name);
        user_msg = view.findViewById(R.id.user_msg);
        user_photo = view.findViewById(R.id.user_photo);
        user_photo.setOnClickListener(userPhotoIVClickListener);

        LinearLayout rootLinear = view.findViewById(R.id.linear_root);
        rootLinear.requestFocus();

        Button saveBtn = view.findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(saveBtnClickListener);
        Button changePWBtn = view.findViewById(R.id.changePWBtn);
        changePWBtn.setOnClickListener(changePWBtnClickListener);

        getUserInfoFromServer();
        return view;
    }

    void getUserInfoFromServer(){
        final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DocumentReference docRef = FirebaseFirestore.getInstance().collection("users").document(uid);
        docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                userModel = documentSnapshot.toObject(UserModel.class);

                if (userModel.getUid() == null) { // if condition added by hari

                    String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                    String token = userModel.getToken();
                    userModel = new UserModel();
                    userModel.setToken(token);
                    userModel.setUid(uid);
                    userModel.setUserid(email);
                    userModel.setUsernm(extractIDFromEmail(email));
                    userModel.setUsermsg("...");
                }else {

                    user_id.setText(userModel.getUserid());
                    user_name.setText(userModel.getUsernm());
                    user_msg.setText(userModel.getUsermsg());
                }

                if (userModel.getUserphoto()!= null && !"".equals(userModel.getUserphoto())) {

                    StorageReference photoStorageReference = FirebaseStorage.getInstance().getReference("userPhoto/"+userModel.getUserphoto());
                    photoStorageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {

                            Glide.with(getActivity())
                                    .load(uri)
                                    .into(user_photo);
                        }
                    });
                }
            }
        });
    }

    String extractIDFromEmail(String email){

        if (email != null){

            String[] parts = email.split("@");
            return parts[0];
        }
        return "";
    }

    Button.OnClickListener userPhotoIVClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
            startActivityForResult(intent, PICK_FROM_ALBUM);
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==PICK_FROM_ALBUM && resultCode== getActivity().RESULT_OK) {
            user_photo.setImageURI(data.getData());
            userPhotoUri = data.getData();
        }
    }

    Button.OnClickListener saveBtnClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            if (!validateForm()) return;
            userModel.setUsernm(user_name.getText().toString());
            userModel.setUsermsg(user_msg.getText().toString());

            final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            final FirebaseFirestore db = FirebaseFirestore.getInstance();

            if (userPhotoUri!=null) {
                userModel.setUserphoto( uid );
            }

            db.collection("users").document(uid)
                    .set(userModel)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            if (userPhotoUri==null) {
                                Util9.showMessage(getActivity(), "Success to Save.");
                            } else {
                                // small image
                                Glide.with(getContext())
                                        .asBitmap()
                                        .load(userPhotoUri)
                                        .apply(new RequestOptions().override(150, 150))
                                        .into(new SimpleTarget<Bitmap>() {
                                            @Override
                                            public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                                                byte[] data = baos.toByteArray();
                                                FirebaseStorage.getInstance().getReference().child("userPhoto/" + uid).putBytes(data);
                                                Util9.showMessage(getActivity(), "Success to Save.");
                                            }
                                        });
                            }
                            mainActivityInterface.updateUserDetails(userPhotoUri);
                        }
                    });
        }
    };

    Button.OnClickListener changePWBtnClickListener = new View.OnClickListener() {
        public void onClick(final View view) {
            startActivity(new Intent(getActivity(), UserPWActivity.class));
        }
    };

    private boolean validateForm() {
        boolean valid = true;

        String userName = user_name.getText().toString();
        if (TextUtils.isEmpty(userName)) {
            user_name.setError("Required.");
            valid = false;
        } else {
            user_name.setError(null);
        }

        String userMsg = user_msg.getText().toString();
        if (TextUtils.isEmpty(userMsg)) {
            user_msg.setError("Required.");
            valid = false;
        } else {
            user_msg.setError(null);
        }
        Util9.hideKeyboard(getActivity());

        return valid;
    }

}

package com.example.arcore_cloudanchors;

import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class MainActivity extends AppCompatActivity {

    private CustomArFragment fragment;
    private Anchor cloudAnchor;

    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }

    private AppAnchorState appAnchorState = AppAnchorState.NONE;
    private SnackbarHelper snackBarHelper = new SnackbarHelper();
    private final StorageManager storageManager = new StorageManager();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        fragment.getPlaneDiscoveryController().hide();
        fragment.getArSceneView().getScene().setOnUpdateListener(this::onUpdateFrame);
        Button clearButton = findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                setCloudAnchor(null);
            }
        });

        fragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING ||
                        appAnchorState != AppAnchorState.NONE) {
                        return;
                    }

                    Anchor newAnchor = fragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());
                    setCloudAnchor(newAnchor);

                    appAnchorState = AppAnchorState.HOSTING;
                    snackBarHelper.showMessage(this, "Now hosting anchor...");

                    placeObject(fragment, cloudAnchor, Uri.parse("Fox.sfb"));
                }
        );
    }

    private void setCloudAnchor(Anchor newAnchor){
        if (cloudAnchor != null){
            cloudAnchor.detach();
        }

        cloudAnchor = newAnchor;
        appAnchorState = AppAnchorState.NONE;
        snackBarHelper.hide(this);
    }

    Button resolveButton = findViewById(R.id.resolve_button);
    resolveButton.setOnClickListener(new View.OnClickListener() {
       @Override
       public void onClick(View view) {
           if (cloudAnchor != null){
               snackBarHelper.showMessageWithDismiss(getParent(), "Please clear Anchor");
               return;
           }
           ResolveDialogFragment dialog = new ResolveDialogFragment();
           dialog.setOkListener(MainActivity.this::onResolveOkPressed);
           dialog.show(getSupportFragmentManager(), "Resolve");
        }
    });

    private void onResolveOkPressed(String dialogValue){
        int shortCode = Integer.parseInt(dialogValue);
        String cloudAnchorId = storageManager.getCloudAnchorID(this, shortCode);
        Anchor resolvedAnchor = fragment.getArSceneView().getSession().resolveCloudAnchor(cloudAnchorId);
        setCloudAnchor(resolvedAnchor);
        placeObject(fragment, cloudAnchor, Uri.parse("Fox.sfb"));
        snackBarHelper.showMessage(this, "Now resolving anchor...");
        appAnchorState = AppAnchorState.RESOLVING;
    }

    private void onUpdateFrame(FrameTime frameTime) {
        checkUpdateAnchor();
    }

    private synchronized void checkUpdateAnchor(){
        if (appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING) {
            return;
        }
        Anchor.CloudAnchorState cloudState = cloudAnchor.getCloudAnchorState();
        if (appAnchorState == AppAnchorState.HOSTING) {
            if(cloudState.isError()){
                snackBarHelper.showMessageWithDismiss(this, "Error");
                appAnchorState = AppAnchorState.NONE;
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                int shortCode = storageManager.nextShortCode(this);
                storageManager.storeUsingShortCode(this, shortCode, cloudAnchor.getCloudAnchorId());
                snackBarHelper.showMessageWithDismiss(this, "Anchor hosted! Cloud Short Code: " + shortCode);
                appAnchorState = AppAnchorState.HOSTED;
            }
        } else if (appAnchorState == AppAnchorState.RESOLVING) {
            if(cloudState.isError()){
                snackBarHelper.showMessageWithDismiss(this, "Error");
                appAnchorState = AppAnchorState.NONE;
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                appAnchorState = AppAnchorState.RESOLVED;
            }
        }

    };

    private void placeObject(ArFragment fragment, Anchor anchor, Uri model){
        ModelRenderable.builder()
                .setSource(fragment.getContext(), model)
                .build()
                .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                .exceptionally((throwable -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(throwable.getMessage())
                            .setTitle("Error!");
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return null;
                }));
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }
}

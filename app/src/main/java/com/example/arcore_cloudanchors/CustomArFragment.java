package com.example.arcore_cloudanchors;

import android.graphics.Bitmap;

import com.google.ar.core.Config;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.core.Session;

public class CustomArFragment extends ArFragment {
    @Override
    protected Config getSessionConfiguration(Session session){
        getPlaneDiscoveryController().setInstructionView(null);
        Config config = super.getSessionConfiguration(session);
        config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);
        return config;
    }
}

package jp.wasabeef.richeditor;

import android.content.Context;
import android.webkit.JavascriptInterface;

import java.io.IOException;

/**
 * Created by Vignesh Ravi on 30/8/2016.
 */

public class JavaScriptInterface {

    @JavascriptInterface
    public String getFileContents(Context mContext){
        try{
            String javaScript = mContext.getAssets().open("jquery-3.1.0.js").toString();
            return javaScript;
        }catch (IOException e){
            return null;
        }
    }
}

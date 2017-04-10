package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.ContentDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * Created by yuta1723 on 2017/04/10.
 */

public class DownloadedHlsDataSource implements DataSource {

    private static final String SCHEME_ASSET = "asset";
    private static final String SCHEME_CONTENT = "content";

    private final DataSource baseDataSource;
    private final DataSource fileDataSource;
    private final DataSource assetDataSource;
    private final DataSource contentDataSource;
    private final Uri uri;
    private DataSource dataSource;

    private static final int MAX_BYTEBUFFER_LENGTH = 1024;
    private InputStream is = null;
    //inputStream がデータの鍵の有無の判定基準になってもいいのか?
    //byteやflagとかもっといいのがないのか? > flagの方が簡単?

    private String TAG = DownloadedHlsDataSource.class.getSimpleName();

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context                     A context.
     * @param listener                    An optional listener.
     * @param userAgent                   The User-Agent string that should be used when requesting remote data.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *                                    to HTTPS and vice versa) are enabled when fetching remote data.
     */
    public DownloadedHlsDataSource(Context context, TransferListener<? super DataSource> listener,
                                   String userAgent, boolean allowCrossProtocolRedirects, Uri uri) {
        this(context, listener, userAgent, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects, uri);
    }

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context                     A context.
     * @param listener                    An optional listener.
     * @param userAgent                   The User-Agent string that should be used when requesting remote data.
     * @param connectTimeoutMillis        The connection timeout that should be used when requesting remote
     *                                    data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param readTimeoutMillis           The read timeout that should be used when requesting remote data,
     *                                    in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *                                    to HTTPS and vice versa) are enabled when fetching remote data.
     */
    public DownloadedHlsDataSource(Context context, TransferListener<? super DataSource> listener,
                                   String userAgent, int connectTimeoutMillis, int readTimeoutMillis,
                                   boolean allowCrossProtocolRedirects, Uri uri) {
        this(context, listener,
                new DefaultHttpDataSource(userAgent, null, listener, connectTimeoutMillis,
                        readTimeoutMillis, allowCrossProtocolRedirects), uri);
        Log.d(TAG,"enter DownloadedHlsDataSource");
    }

    /**
     * Constructs a new instance that delegates to a provided {@link DataSource} for URI schemes other
     * than file, asset and content.
     *
     * @param context        A context.
     * @param listener       An optional listener.
     * @param baseDataSource A {@link DataSource} to use for URI schemes other than file, asset and
     *                       content. This {@link DataSource} should normally support at least http(s).
     */
    public DownloadedHlsDataSource(Context context, TransferListener<? super DataSource> listener,
                                   DataSource baseDataSource, Uri uri) {
        this.baseDataSource = Assertions.checkNotNull(baseDataSource);
        this.fileDataSource = new FileDataSource(listener);
        this.assetDataSource = new AssetDataSource(context, listener);
        this.contentDataSource = new ContentDataSource(context, listener);
        this.uri = uri;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
//        dataSpecが鍵かtsかm3u8か判定する。
        if(!dataSpec.uri.getLastPathSegment().endsWith(".ts") && !dataSpec.uri.getLastPathSegment().endsWith(".m3u8")) {
            String origin = dataSpec.uri.toString();
            String saveDir = this.uri.toString().substring(0, this.uri.getPath().lastIndexOf("/")) + "/";
            String keyname = dataSpec.uri.toString().substring(dataSpec.uri.toString().lastIndexOf("/") + 1, dataSpec.uri.toString().length());

            //ここで鍵の名前をhash化して読み込み
            //とりあえずmd5

            String fileName = makeHashName(keyname);
            dataSpec = new DataSpec(Uri.parse(saveDir + fileName), dataSpec.postBody, dataSpec.absoluteStreamPosition, dataSpec.position, dataSpec.length,
                    dataSpec.key, dataSpec.flags);

            File EncryptedKeyFile = new File(new File(saveDir), fileName);
            if (!EncryptedKeyFile.exists()) {
                Log.d(TAG,"key file is not available" );
                return 0;
            }
            is = new ByteArrayInputStream(loadEncryptedKey(EncryptedKeyFile));
            Log.d(TAG,"key is " + is.toString());
            dataSpec = new DataSpec(Uri.parse(saveDir + fileName), loadEncryptedKey(EncryptedKeyFile), dataSpec.absoluteStreamPosition, dataSpec.position, dataSpec.length,
                    dataSpec.key, dataSpec.flags);
            return is.available();


        } else if (dataSpec.uri.getPath().lastIndexOf("/") != this.uri.getPath().lastIndexOf("/")) {
            //todo tsファイルの場合

            // 相対パスを修正
            String origin = dataSpec.uri.getPath();
            String saveDir= this.uri.toString().substring(0,this.uri.getPath().lastIndexOf("/"));
//            String dir = origin.substring(0, this.uri.getPath().lastIndexOf("/"));
            String fileName = origin.substring(origin.lastIndexOf("/"));
            dataSpec = new DataSpec(Uri.parse(saveDir + fileName), dataSpec.postBody, dataSpec.absoluteStreamPosition, dataSpec.position, dataSpec.length,
                    dataSpec.key, dataSpec.flags);
        }
        is = null;
        Assertions.checkState(dataSource == null);
        // Choose the correct source for the scheme.
        String scheme = dataSpec.uri.getScheme();
        if (Util.isLocalFileUri(dataSpec.uri)) {
            if (dataSpec.uri.getPath().startsWith("/android_asset/")) {
                dataSource = assetDataSource;
            } else {
                dataSource = fileDataSource;
            }
        } else if (SCHEME_ASSET.equals(scheme)) {
            dataSource = assetDataSource;
        } else if (SCHEME_CONTENT.equals(scheme)) {
            dataSource = contentDataSource;
        } else {
            dataSource = baseDataSource;
        }
        // Open the source and return.
        return dataSource.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return is != null ? is.read(buffer, offset, readLength) : dataSource.read(buffer, offset, readLength);
    }

    @Override
    public Uri getUri() {
        return dataSource == null ? null : dataSource.getUri();
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            try {
                dataSource.close();
            } finally {
                dataSource = null;
            }
        }
    }
    private String makeHashName(String name){
        String hashedName = "";
        try {
            byte[] str_bytes = name.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] md5_bytes = md.digest(str_bytes);
            BigInteger big_int = new BigInteger(1, md5_bytes);
            hashedName = big_int.toString(16);
            Log.d(TAG,"name : " + name +  " HashedName : " + hashedName);
            return hashedName;
        }catch(Exception e){
            return "";
            // 略
        }
    }

    private byte[] loadEncryptedKey(File file) {
        Log.d(TAG, "loadEncryptedKey");
        final int size = (int) file.length();
        if (size == 0 || size > MAX_BYTEBUFFER_LENGTH) {
            Log.d(TAG, "loadEncryptedKey: File size is incorrect: " + size);
            return null;
        }
        FileInputStream fis = null;
        byte[] data = new byte[size];
        try {
            fis = new FileInputStream(file);
            if (size != fis.read(data, 0, size)) {
                Log.d(TAG, "loadEncryptedKey: Could not load data");
                return null;
            } else {
                Log.d(TAG, "loadEncryptedKey: Load keySetId successfully");
                return data;
            }
        } catch (IOException e) {
            Log.d(TAG, "loadEncryptedKey: Unable to load keySetId", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
                fis = null;
            }
        }
        return null;
    }
}
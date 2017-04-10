package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Created by yuta1723 on 2017/04/10.
 */

public class DownloadedHlsDataSourceFactory implements DataSource.Factory {

    private final Context context;
    private final TransferListener<? super DataSource> listener;
    private final DataSource.Factory baseDataSourceFactory;
    private final Uri uri;

    /**
     * @param context A context.
     * @param userAgent The User-Agent string that should be used.
     */
    public DownloadedHlsDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, null);
    }

    /**
     * @param context A context.
     * @param userAgent The User-Agent string that should be used.
     * @param listener An optional listener.
     */
    public DownloadedHlsDataSourceFactory(Context context, String userAgent,
                                          TransferListener<? super DataSource> listener) {
        this(context, listener, new DefaultHttpDataSourceFactory(userAgent, listener), null);
    }

    /**
     * @param context A context.
     * @param listener An optional listener.
     * @param baseDataSourceFactory A {@link DataSource.Factory} to be used to create a base {@link DataSource}
     *     for {@link DefaultDataSource}.
     */
    public DownloadedHlsDataSourceFactory(Context context, TransferListener<? super DataSource> listener,
                                          DataSource.Factory baseDataSourceFactory, Uri uri) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.baseDataSourceFactory = baseDataSourceFactory;
        this.uri = uri;
    }

    @Override
    public DownloadedHlsDataSource createDataSource() {
        return new DownloadedHlsDataSource(context, listener, baseDataSourceFactory.createDataSource(), uri);
    }


}

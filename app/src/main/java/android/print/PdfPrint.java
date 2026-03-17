package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

/**
 * WebView の PrintDocumentAdapter から直接 PDF ファイルを生成するユーティリティ。
 *
 * ⚠ このクラスは意図的に android.print パッケージに配置しています。
 * LayoutResultCallback / WriteResultCallback は @hide API であり、
 * 同一パッケージからでないとアクセスできません。
 * 将来の Android バージョンで動作しなくなるリスクがあります。
 */
public class PdfPrint {

    public interface PrintCallback {
        void onSuccess();

        void onFailure(String errorMsg);
    }

    public static void print(PrintDocumentAdapter adapter, PrintAttributes attributes, ParcelFileDescriptor pfd,
            PrintCallback callback) {
        adapter.onLayout(null, attributes, new CancellationSignal(), new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                adapter.onWrite(new PageRange[] { PageRange.ALL_PAGES }, pfd, new CancellationSignal(),
                        new PrintDocumentAdapter.WriteResultCallback() {
                            @Override
                            public void onWriteFinished(PageRange[] pages) {
                                super.onWriteFinished(pages);
                                callback.onSuccess();
                            }

                            @Override
                            public void onWriteFailed(CharSequence error) {
                                super.onWriteFailed(error);
                                callback.onFailure(error != null ? error.toString() : "Write failed");
                            }

                            @Override
                            public void onWriteCancelled() {
                                super.onWriteCancelled();
                                callback.onFailure("Write cancelled");
                            }
                        });
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                super.onLayoutFailed(error);
                callback.onFailure(error != null ? error.toString() : "Layout failed");
            }

            @Override
            public void onLayoutCancelled() {
                super.onLayoutCancelled();
                callback.onFailure("Layout cancelled");
            }
        }, null);
    }
}

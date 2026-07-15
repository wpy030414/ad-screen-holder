package im.xrl.ad_screen_holder;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.text.InputType;

/**
 * 不可见视图：拦截虚拟键盘（IME）的 commitText 输入，通过 {@link OnCharListener} 回调。
 * <p>
 * 继承 {@link View} 而非 EditText，配合三道防线防止输入法自动弹出：
 * <ol>
 *   <li>focusableInTouchMode = false — 触摸不抢焦点</li>
 *   <li>SOFT_INPUT_STATE_ALWAYS_HIDDEN — Window 级抑制</li>
 *   <li>requestFocus 后立即 hideSoftInputFromWindow</li>
 * </ol>
 * <p>
 * 物理键盘仍走 Activity 的 dispatchKeyEvent / onKeyDown，不受此 View 影响。
 */
public class ImeInterceptorView extends View {

    /** 字符回调监听器 */
    public interface OnCharListener {
        /**
         * 当 IME 通过 commitText 提交字符时调用。
         * @param c 已转为小写的字符
         */
        void onChar(char c);
    }

    private OnCharListener mListener;

    public ImeInterceptorView(Context context) {
        super(context);
        init();
    }

    public ImeInterceptorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ImeInterceptorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        // 禁止触摸获取焦点：防止用户点击屏幕时触发输入法弹出
        setFocusableInTouchMode(false);
    }

    public void setOnCharListener(OnCharListener listener) {
        mListener = listener;
    }

    /**
     * 声明本视图可接收 IME 文本输入（非硬件键盘事件）。
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    /**
     * 为 IME 提供自定义 InputConnection，拦截 commitText 调用。
     * inputType 设为 visiblePassword 以禁用自动纠错/联想。
     */
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN;

        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() > 0 && mListener != null) {
                    mListener.onChar(Character.toLowerCase(text.charAt(0)));
                }
                return true;
            }
        };
    }
}

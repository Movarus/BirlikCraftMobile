package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.kdt.pojavlaunch.R;

/**
 * Keyboard helper for the launcher:
 * - the full-screen landscape keyboard editor is never used, only the keyboard is shown;
 * - while typing, the focused field is shown magnified in a bar right above the keyboard.
 */
public final class BirlikTypingPreview implements ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener, TextWatcher {

    private final View mRoot;
    private final View mBar;
    private final TextView mLabel;
    private final TextView mText;
    private EditText mField;

    private BirlikTypingPreview(View root, View bar) {
        mRoot = root;
        mBar = bar;
        mLabel = bar.findViewById(R.id.birlik_typing_preview_label);
        mText = bar.findViewById(R.id.birlik_typing_preview_text);
    }

    /** Call after setContentView(). */
    public static void attach(@NonNull Activity activity) {
        View bar = activity.findViewById(R.id.birlik_typing_preview);
        View root = activity.findViewById(android.R.id.content);
        if (bar == null || root == null) return;
        BirlikTypingPreview preview = new BirlikTypingPreview(root, bar);
        ViewTreeObserver observer = root.getViewTreeObserver();
        observer.addOnGlobalFocusChangeListener(preview);
        observer.addOnGlobalLayoutListener(preview);
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (mField != null) mField.removeTextChangedListener(this);
        mField = newFocus instanceof EditText ? (EditText) newFocus : null;
        if (mField != null) {
            disableFullscreenEditor(mField);
            mField.addTextChangedListener(this);
            CharSequence hint = mField.getHint();
            mLabel.setText(hint == null ? "" : hint);
            updateText();
        }
        updateVisibility();
    }

    @Override
    public void onGlobalLayout() {
        updateVisibility();
    }

    private void updateVisibility() {
        boolean keyboardOpen = false;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mRoot);
        if (insets != null) keyboardOpen = insets.isVisible(WindowInsetsCompat.Type.ime());
        int visibility = (mField != null && mField.isShown() && keyboardOpen) ? View.VISIBLE : View.GONE;
        if (mBar.getVisibility() != visibility) mBar.setVisibility(visibility);
    }

    private void updateText() {
        if (mField == null) return;
        CharSequence text = mField.getText();
        int variation = mField.getInputType() & InputType.TYPE_MASK_VARIATION;
        boolean password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        if (password) {
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < text.length(); i++) dots.append('•');
            mText.setText(dots);
        } else {
            mText.setText(text);
        }
    }

    /** Make sure the keyboard does not open its own full-screen editor in landscape. */
    private static void disableFullscreenEditor(EditText field) {
        int options = field.getImeOptions();
        if ((options & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0) return;
        field.setImeOptions(options | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        InputMethodManager imm = (InputMethodManager) field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.restartInput(field);
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override public void afterTextChanged(Editable s) { updateText(); }
}

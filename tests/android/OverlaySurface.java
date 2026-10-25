package local.sliverwolf.assistant.tests;

/** Test-only blank foreground surface; avoids recording unrelated apps under the overlay. */
public final class OverlaySurface extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        android.widget.LinearLayout layout=new android.widget.LinearLayout(this);
        layout.setOrientation(1);layout.setPadding(24,120,24,24);layout.setBackgroundColor(0xff101827);
        android.widget.TextView label=new android.widget.TextView(this);
        label.setText("Controlled overlay test\nNo personal content");label.setTextColor(0xffd5deed);label.setTextSize(20);layout.addView(label);
        android.widget.EditText input=new android.widget.EditText(this);input.setHint("Controlled keyboard field");
        input.setTextColor(0xffd5deed);input.setHintTextColor(0xffa0aaba);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        layout.addView(input);setContentView(layout);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if(getIntent().getBooleanExtra("keyboard",false))input.postDelayed(()->{
            input.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        },600);
    }
}

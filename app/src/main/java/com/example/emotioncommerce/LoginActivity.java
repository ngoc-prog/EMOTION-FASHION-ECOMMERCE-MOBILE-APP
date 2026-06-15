package com.example.emotioncommerce;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.emotioncommerce.data.AuthRepository;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_FROM_CHECKOUT = "from_checkout";
    static final String PREFS_NAME  = "lume_auth_prefs";
    static final String KEY_EMAIL   = "remembered_email";
    static final String KEY_PASS    = "remembered_password";
    static final String KEY_REMEMBER = "auto_login";

    private boolean fromCheckout;
    private EditText etEmail, etPassword;
    private CheckBox cbRemember;
    private TextView tvError;

    private final ActivityResultLauncher<Intent> registerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                setResult(RESULT_OK);
                finish();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        fromCheckout = getIntent().getBooleanExtra(EXTRA_FROM_CHECKOUT, false);

        etEmail    = findViewById(R.id.et_login_email);
        etPassword = findViewById(R.id.et_login_password);
        cbRemember = findViewById(R.id.cb_remember_me);
        tvError    = findViewById(R.id.tv_login_error);

        if (fromCheckout) {
            ((TextView) findViewById(R.id.tv_login_subtitle))
                    .setText(getString(R.string.login_to_checkout));
            findViewById(R.id.btn_login_skip).setVisibility(View.GONE);
        }

        // Pre-fill saved credentials
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_REMEMBER, false)) {
            etEmail.setText(prefs.getString(KEY_EMAIL, ""));
            etPassword.setText(prefs.getString(KEY_PASS, ""));
            cbRemember.setChecked(true);
        }

        hideHintOnFocus(etEmail);
        hideHintOnFocus(etPassword);

        findViewById(R.id.btn_login).setOnClickListener(v -> attemptLogin());

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                attemptLogin();
                return true;
            }
            return false;
        });

        findViewById(R.id.tv_go_register).setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra(EXTRA_FROM_CHECKOUT, fromCheckout);
            registerLauncher.launch(intent);
        });

        findViewById(R.id.btn_login_skip).setOnClickListener(v -> finish());
    }

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        tvError.setVisibility(View.GONE);

        if (email.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.err_login_empty));
            return;
        }

        AuthRepository.LoginResult result = AuthRepository.getInstance().login(email, password);

        switch (result) {
            case SUCCESS_ADMIN:
            case SUCCESS_CUSTOMER:
                saveOrClearRemembered(email, password);
                if (result == AuthRepository.LoginResult.SUCCESS_ADMIN) {
                    startActivity(new Intent(this, AdminAnalyticsActivity.class));
                }
                setResult(RESULT_OK);
                finish();
                break;

            case WRONG_PASSWORD:
                showError(getString(R.string.err_wrong_password));
                etPassword.setText("");
                break;

            case NOT_FOUND:
                showError(getString(R.string.err_email_not_registered));
                break;
        }
    }

    private void saveOrClearRemembered(String email, String password) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        if (cbRemember.isChecked()) {
            editor.putString(KEY_EMAIL, email)
                  .putString(KEY_PASS, password)
                  .putBoolean(KEY_REMEMBER, true);
        } else {
            editor.remove(KEY_EMAIL).remove(KEY_PASS).putBoolean(KEY_REMEMBER, false);
        }
        editor.apply();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private static void hideHintOnFocus(EditText et) {
        CharSequence original = et.getHint();
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) et.setHint("");
            else if (et.getText().toString().isEmpty()) et.setHint(original);
        });
    }
}

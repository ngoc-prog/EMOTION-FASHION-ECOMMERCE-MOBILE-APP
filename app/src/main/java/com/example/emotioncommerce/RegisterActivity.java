package com.example.emotioncommerce;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.emotioncommerce.data.AuthRepository;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirm;
    private TextView tvError;
    private boolean fromCheckout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        fromCheckout = getIntent().getBooleanExtra(LoginActivity.EXTRA_FROM_CHECKOUT, false);

        etName    = findViewById(R.id.et_register_name);
        etEmail   = findViewById(R.id.et_register_email);
        etPassword = findViewById(R.id.et_register_password);
        etConfirm = findViewById(R.id.et_register_confirm);
        tvError   = findViewById(R.id.tv_register_error);

        hideHintOnFocus(etName);
        hideHintOnFocus(etEmail);
        hideHintOnFocus(etPassword);
        hideHintOnFocus(etConfirm);

        findViewById(R.id.btn_register_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_go_login).setOnClickListener(v -> finish());
        findViewById(R.id.btn_register).setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm  = etConfirm.getText().toString();
        tvError.setVisibility(View.GONE);

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Mật khẩu xác nhận không khớp");
            return;
        }

        AuthRepository.RegisterResult result =
                AuthRepository.getInstance().register(name, email, password);

        switch (result) {
            case SUCCESS:
                setResult(RESULT_OK);
                finish();
                break;
            case EMAIL_EXISTS:
                showError("Email này đã được đăng ký");
                break;
            case WEAK_PASSWORD:
                showError("Mật khẩu phải có ít nhất 6 ký tự");
                break;
        }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private static void hideHintOnFocus(EditText et) {
        CharSequence original = et.getHint();
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                et.setHint("");
            } else if (et.getText().toString().isEmpty()) {
                et.setHint(original);
            }
        });
    }
}

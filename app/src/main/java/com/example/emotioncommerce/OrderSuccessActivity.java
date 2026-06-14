package com.example.emotioncommerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class OrderSuccessActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_NUMBER = "order_number";
    public static final String EXTRA_ORDER_TOTAL  = "order_total";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_success);

        String orderNum = getIntent().getStringExtra(EXTRA_ORDER_NUMBER);
        long total      = getIntent().getLongExtra(EXTRA_ORDER_TOTAL, 0);

        ((TextView) findViewById(R.id.tv_order_number))
                .setText(orderNum != null ? orderNum : "");
        ((TextView) findViewById(R.id.tv_order_total_success))
                .setText(getString(R.string.total_payment_fmt, total));

        // Back to shopping — clear back stack to MainActivity
        findViewById(R.id.btn_continue_shopping).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Prevent back to checkout
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

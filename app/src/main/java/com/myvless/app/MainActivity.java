package com.myvless.app;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST_CODE = 100;

    private Button btnToggle;
    private TextView txtStatus;
    private ListView listNodes;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;

    private List<Node> nodes = new ArrayList<>();
    private NodeListAdapter adapter;
    private SubscriptionManager subManager;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btn_toggle);
        txtStatus = findViewById(R.id.txt_status);
        listNodes = findViewById(R.id.list_nodes);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(android.R.id.empty); // placeholder

        subManager = new SubscriptionManager(this);

        adapter = new NodeListAdapter(this, nodes);
        listNodes.setAdapter(adapter);
        listNodes.setOnItemClickListener((p, v, pos, id) -> selectNode(pos));
        listNodes.setOnItemLongClickListener((p, v, pos, id) -> {
            deleteNode(pos);
            return true;
        });

        btnToggle.setOnClickListener(v -> toggleVpn());
        swipeRefresh.setOnRefreshListener(this::refreshNodes);

        loadNodes();
    }

    private void loadNodes() {
        nodes.clear();
        nodes.addAll(subManager.loadNodes());
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void refreshNodes() {
        String url = getPreferences(MODE_PRIVATE).getString("sub_url", "");
        if (url.isEmpty()) {
            Toast.makeText(this, "Set subscription URL first", Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            showUrlDialog();
            return;
        }
        String mirror = getPreferences(MODE_PRIVATE).getString("sub_mirror", "");
        subManager.fetchSubscription(url, mirror, (fetched, err) -> {
            runOnUiThread(() -> {
                swipeRefresh.setRefreshing(false);
                if (fetched != null && !fetched.isEmpty()) {
                    nodes.clear();
                    nodes.addAll(fetched);
                    subManager.saveNodes(fetched);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Updated " + fetched.size() + " servers", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed: " + err, Toast.LENGTH_SHORT).show();
                }
                updateStatus();
            });
        });
    }

    private void selectNode(int pos) {
        if (pos < 0 || pos >= nodes.size()) return;
        if (isConnected) {
            Toast.makeText(this, "Disconnect first", Toast.LENGTH_SHORT).show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putInt("active_idx", pos).apply();
        Toast.makeText(this, nodes.get(pos).remark + " selected", Toast.LENGTH_SHORT).show();
    }

    private void deleteNode(int pos) {
        if (pos < 0 || pos >= nodes.size()) return;
        if (isConnected) return;
        nodes.remove(pos);
        subManager.saveNodes(nodes);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
    }

    private void toggleVpn() {
        if (isConnected) {
            stopVpn();
        } else {
            if (nodes.isEmpty()) {
                Toast.makeText(this, "No servers. Add a subscription.", Toast.LENGTH_SHORT).show();
                showUrlDialog();
                return;
            }
            startVpn();
        }
    }

    private void startVpn() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent vpnIntent = new Intent(this, MyVpnService.class);
            vpnIntent.setAction(MyVpnService.ACTION_CONNECT);
            startForegroundService(vpnIntent);
            isConnected = true;
            updateStatus();
        }
    }

    private void stopVpn() {
        Intent vpnIntent = new Intent(this, MyVpnService.class);
        vpnIntent.setAction(MyVpnService.ACTION_DISCONNECT);
        startService(vpnIntent);
        isConnected = false;
        updateStatus();
    }

    private void updateStatus() {
        txtStatus.setText(nodes.size() + " servers" + (isConnected ? " ● Connected" : " ○ Disconnected"));
        btnToggle.setText(isConnected ? R.string.connected : R.string.disconnected);
    }

    private void showUrlDialog() {
        String currentUrl = getPreferences(MODE_PRIVATE).getString("sub_url", "");
        String currentMirror = getPreferences(MODE_PRIVATE).getString("sub_mirror", "");
        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Subscription URL");
        builder.setView(R.layout.dialog_subscription);
        builder.setPositiveButton("Save", (dialog, which) -> {
            // TODO: read text fields from the dialog layout
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}

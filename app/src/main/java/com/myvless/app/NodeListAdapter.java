package com.myvless.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class NodeListAdapter extends ArrayAdapter<Node> {
    public NodeListAdapter(Context context, List<Node> nodes) {
        super(context, 0, nodes);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        }
        Node node = getItem(position);
        TextView text1 = convertView.findViewById(android.R.id.text1);
        TextView text2 = convertView.findViewById(android.R.id.text2);
        if (node != null) {
            String remark = (node.remark != null && !node.remark.isEmpty()) ? node.remark : node.server;
            text1.setText(remark);
            String pingStr = (node.ping < 9999) ? "Ping: " + node.ping + " ms" : "Ping: ? ms";
            text2.setText(node.server + ":" + node.port + " | " + pingStr);
        }
        return convertView;
    }
}

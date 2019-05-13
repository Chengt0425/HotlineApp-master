package com.example.chengt.hotlineapp;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class TextMessageAdapter extends BaseAdapter {
    private List<TextMessage> messages = new ArrayList<>();
    private Context context;

    TextMessageAdapter(Context context) {
        this.context = context;
    }

    public void add(TextMessage message) {
        this.messages.add(message);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Object getItem(int i) {
        return messages.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        TextMessageViewHolder holder = new TextMessageViewHolder();
        LayoutInflater messageInflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        TextMessage message = messages.get(i);

        if (message.isBelongsToCurrentUser()) {
            convertView = messageInflater.inflate(R.layout.my_message, null);
            holder.messageBody = convertView.findViewById(R.id.my_message_body);
            convertView.setTag(holder);
            holder.messageBody.setText(message.getText());
        }
        else {
            convertView = messageInflater.inflate(R.layout.their_message, null);
            holder.messageBody = convertView.findViewById(R.id.their_message_body);
            convertView.setTag(holder);
            holder.messageBody.setText(message.getText());
        }

        return convertView;
    }
}

class TextMessageViewHolder {
    public TextView name;
    public TextView messageBody;
}

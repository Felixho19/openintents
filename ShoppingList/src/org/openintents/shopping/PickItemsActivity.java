package org.openintents.shopping;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

public class PickItemsActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.shopping_pick_items);

		final ShoppingListView mListItems = (ShoppingListView) findViewById(R.id.list_items);
		mListItems.mMode = ShoppingActivity.MODE_ADD_ITEMS;

		String listId = getIntent().getData().getLastPathSegment();
		startManagingCursor(mListItems.fillItems(Long.parseLong(listId)));
		mListItems.setListTheme(ShoppingListView.MARK_CHECKBOX);
		mListItems.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView parent, View v, int pos, long id) {
				mListItems.toggleItemRemovedFromList(pos);
				v.invalidate();
			}

		});

	}

}
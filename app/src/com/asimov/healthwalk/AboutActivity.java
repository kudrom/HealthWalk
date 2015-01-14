package com.asimov.healthwalk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.view.MenuItem;

import com.asimov.healthwalk.R;


/**
 * Actividad para mostrar informacion sobre la aplicacion
 * @author Stoyan Veselinov Andreev
 *
 */
public class AboutActivity extends Activity {
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
    
		getActionBar().setDisplayHomeAsUpEnabled(true);

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    	case android.R.id.home:
	    		Intent upIntent = NavUtils.getParentActivityIntent(this);
	            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
	            	TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(upIntent)
                    .startActivities();
            } else {
                upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                NavUtils.navigateUpTo(this, upIntent);
            }
	    		return true;
	    }
	    return super.onOptionsItemSelected(item);
	}

}

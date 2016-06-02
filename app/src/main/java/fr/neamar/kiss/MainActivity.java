package fr.neamar.kiss;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;

import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.result.Result;
import fr.neamar.kiss.searcher.ApplicationsSearcher;
import fr.neamar.kiss.searcher.HistorySearcher;
import fr.neamar.kiss.searcher.NullSearcher;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.searcher.Searcher;
import fr.neamar.kiss.ui.KeyboardScrollHider;
import fr.neamar.kiss.ui.BlockableListView;
import fr.neamar.kiss.ui.BottomPullEffectView;

public class MainActivity extends Activity implements QueryInterface, KeyboardScrollHider.KeyboardHandler {

    public static final String START_LOAD = "fr.neamar.summon.START_LOAD";
    public static final String LOAD_OVER = "fr.neamar.summon.LOAD_OVER";
    public static final String FULL_LOAD_OVER = "fr.neamar.summon.FULL_LOAD_OVER";

    /**
     * IDs for the favorites buttons
     */
    private final int[] favsIds = new int[]{R.id.favorite0, R.id.favorite1, R.id.favorite2, R.id.favorite3};

    /**
     * Number of favorites to retrieve.
     * We need to pad this number to account for removed items still in history
     */
    private final int tryToRetrieve = favsIds.length + 2;
    /**
     * InputType with spellcheck and swiping
     */
    private final static int SPELLCHECK_ENABLED_TYPE = InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
    /**
     * Adapter to display records
     */
    public RecordAdapter adapter;
    /**
     * Store user preferences
     */
    private SharedPreferences prefs;
    private BroadcastReceiver mReceiver;
    /**
     * View for the Search text
     */
    private EditText searchEditText;
    private final Runnable displayKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            showKeyboard();
        }
    };
    /**
     * Main list view
     */
    private ListView list;
    private View     listContainer;
    /**
     * View to display when list is empty
     */
    private View listEmpty;
    /**
     * Utility for automatically hiding the keyboard when scrolling down
     */
    private KeyboardScrollHider hider;
    /**
     * Menu button
     */
    private View menuButton;
    /**
     * Kiss bar
     */
    private View kissBar;
    /**
     * Task launched on text change
     */
    private Searcher searcher;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Initialize UI
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String theme = prefs.getString("theme", "light");
        switch (theme) {
            case "dark":
                setTheme(R.style.AppThemeDark);
                break;
            case "transparent":
                setTheme(R.style.AppThemeTransparent);
                break;
            case "semi-transparent":
                setTheme(R.style.AppThemeSemiTransparent);
                break;
            case "semi-transparent-dark":
                setTheme(R.style.AppThemeSemiTransparentDark);
                break;
            case "transparent-dark":
                setTheme(R.style.AppThemeTransparentDark);
                break;
        }


        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter(START_LOAD);
        IntentFilter intentFilterBis = new IntentFilter(LOAD_OVER);
        IntentFilter intentFilterTer = new IntentFilter(FULL_LOAD_OVER);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(LOAD_OVER)) {
                    updateRecords(searchEditText.getText().toString());
                } else if (intent.getAction().equalsIgnoreCase(FULL_LOAD_OVER)) {
                    // Run GC once to free all the garbage accumulated during provider initialization
                    System.gc();

                    displayLoader(false);

                } else if (intent.getAction().equalsIgnoreCase(START_LOAD)) {
                    displayLoader(true);
                }
            }
        };

        this.registerReceiver(mReceiver, intentFilter);
        this.registerReceiver(mReceiver, intentFilterBis);
        this.registerReceiver(mReceiver, intentFilterTer);
        KissApplication.initDataHandler(this);

        // Initialize preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Lock launcher into portrait mode
        // Do it here (before initializing the view) to make the transition as smooth as possible
        if (prefs.getBoolean("force-portrait", true)) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        setContentView(R.layout.main);

        this.list          = (ListView) this.findViewById(android.R.id.list);
        this.listContainer = (View) this.list.getParent();
        this.listEmpty     = this.findViewById(android.R.id.empty);

        // Create adapter for records
        this.adapter = new RecordAdapter(this, this, R.layout.item_app, new ArrayList<Result>());
        this.list.setAdapter(this.adapter);

        this.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id)
            {
                adapter.onClick(position, v);
            }
        });
        this.adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();

                if(adapter.isEmpty()) {
                    listContainer.setVisibility(View.GONE);
                    listEmpty.setVisibility(View.VISIBLE);
                } else {
                    listContainer.setVisibility(View.VISIBLE);
                    listEmpty.setVisibility(View.GONE);
                }
            }
        });

        searchEditText = (EditText) findViewById(R.id.searchEditText);

        // Listen to changes
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                // Auto left-trim text.
                if (s.length() > 0 && s.charAt(0) == ' ')
                    s.delete(0, 1);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateRecords(s.toString());
                displayClearOnInput();
            }
        });

        // On validate, launch first record
        searchEditText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                RecordAdapter adapter = ((RecordAdapter) list.getAdapter());

                adapter.onClick(adapter.getCount() - 1, null);

                return true;
            }
        });

        kissBar = findViewById(R.id.main_kissbar);
        menuButton = findViewById(R.id.menuButton);
        registerForContextMenu(menuButton);

        this.list.setLongClickable(true);
        this.list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long id) {
                ((RecordAdapter) parent.getAdapter()).onLongClick(pos, v);
                return true;
            }
        });

        this.hider = new KeyboardScrollHider(this,
                (BlockableListView)    this.list,
                (BottomPullEffectView) this.findViewById(R.id.listEdgeEffect)
        );
        this.hider.start();

        // Enable swiping
        if (prefs.getBoolean("enable-spellcheck", false)) {
            searchEditText.setInputType(SPELLCHECK_ENABLED_TYPE);
        }

        // Hide the "X" after the text field, instead displaying the menu button
        displayClearOnInput();

        // Apply effects depending on current Android version
        applyDesignTweaks();
    }

    /**
     * Apply some tweaks to the design, depending on the current SDK version
     */
    private void applyDesignTweaks() {
        final int[] tweakableIds = new int[]{
                R.id.menuButton,
                // Barely visible on the clearbutton, since it disappears instant. Can be seen on long click though
                R.id.clearButton,
                R.id.launcherButton,
                R.id.favorite0,
                R.id.favorite1,
                R.id.favorite2,
                R.id.favorite3,
        };

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);

            for (int id : tweakableIds) {
                findViewById(id).setBackgroundResource(outValue.resourceId);
            }

        } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

            for (int id : tweakableIds) {
                findViewById(id).setBackgroundResource(outValue.resourceId);
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    /**
     * Empty text field on resume and show keyboard
     */
    @SuppressLint("CommitPrefEdits")
    protected void onResume() {
        if (prefs.getBoolean("require-layout-update", false)) {
            // Restart current activity to refresh view, since some preferences
            // may require using a new UI
            prefs.edit().putBoolean("require-layout-update", false).commit();
            Intent i = new Intent(this, getClass());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            finish();
            overridePendingTransition(0, 0);
            startActivity(i);
            overridePendingTransition(0, 0);
        }

        // TODO LOADING
        readTodos();
        
        if (kissBar.getVisibility() != View.VISIBLE) {
            updateRecords(searchEditText.getText().toString());
            displayClearOnInput();
        } else {
            displayKissBar(false);
        }

        // Activity manifest specifies stateAlwaysHidden as windowSoftInputMode
        // so the keyboard will be hidden by default
        // we may want to display it if the setting is set
        if (prefs.getBoolean("display-keyboard", false)) {
            // Display keyboard
            showKeyboard();

            new Handler().postDelayed(displayKeyboardRunnable, 10);
            // For some weird reasons, keyboard may be hidden by the system
            // So we have to run this multiple time at different time
            // See https://github.com/Neamar/KISS/issues/119
            new Handler().postDelayed(displayKeyboardRunnable, 100);
            new Handler().postDelayed(displayKeyboardRunnable, 500);
        } else {
            // Not used (thanks windowSoftInputMode)
            // unless coming back from KISS settings
            hideKeyboard();
        }

        super.onResume();
    }

    protected void readTodos() {
         //Find the view by its id  
        TextView tv = (TextView)findViewById(R.id.todoText);  

        File dir = Environment.getExternalStorageDirectory();  
        //File yourFile = new File(dir, "path/to/the/file/inside/the/sdcard.ext");  

        //Get the text file  
        File file = new File(dir,"writeily/Todos.md");  
        // i have kept text.txt in the sd-card  

        if(file.exists())   // check if file exist  
        {  
              //Read text from file  
            StringBuilder text = new StringBuilder();  

            try {  
                BufferedReader br = new BufferedReader(new FileReader(file));  
                String line;  
                Boolean now = false;
                Boolean first = true;
                text.append("Todos\n");
                while ((line = br.readLine()) != null) { 
                    if (line.toLowerCase().startsWith("#") && now)
                        now = false;
                    else if (line.toLowerCase().startsWith("#todos"))
                        now = true;
                    else if ((line != "\n") 
                            && (line != "\r\n") 
                            && (line != "\r") 
                            && (line.replaceAll("\\r|\\n", "") != ""))
                        if (now) {
                            if (!first)
                                text.append('\n');
                            text.append(line);
                            first = false;
                        }
                }  
            }  
            catch (IOException e) {  
                //You'll need to add proper error handling here  
            }  
            //Set the text  
            tv.setText(text);  
        }  
        else  
        {  
            tv.setText("No todos found");  
        }  

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unregister our receiver
        this.unregisterReceiver(this.mReceiver);
        KissApplication.getCameraHandler().releaseCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        KissApplication.getCameraHandler().releaseCamera();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Empty method,
        // This is called when the user press Home again while already browsing MainActivity
        // onResume() will be called right after, hiding the kissbar if any.
        // http://developer.android.com/reference/android/app/Activity.html#onNewIntent(android.content.Intent)
    }

    @Override
    public void onBackPressed() {
        // Is the kiss menu visible?
        if (menuButton.getVisibility() == View.VISIBLE) {
            displayKissBar(false);
        } else {
            // If no kissmenu, empty the search bar
            searchEditText.setText("");
        }

        // No call to super.onBackPressed, since this would quit the launcher.
    }

    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_MENU:
                // For user with a physical menu button, we still want to display *our* contextual menu
                menuButton.showContextMenu();
                return true;
        }

        return super.onKeyDown(keycode, e);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                return true;
            case R.id.wallpaper:
                hideKeyboard();
                Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                startActivity(Intent.createChooser(intent, getString(R.string.menu_wallpaper)));
                return true;
            case R.id.preferences:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);

        return true;
    }

    /**
     * Display menu, on short or long press.
     *
     * @param menuButton "kebab" menu (3 dots)
     */
    public void onMenuButtonClicked(View menuButton) {
        // When the kiss bar is displayed, the button can still be clicked in a few areas (due to favorite margin)
        // To fix this, we discard any click event occurring when the kissbar is displayed
        if (kissBar.getVisibility() != View.VISIBLE)
            menuButton.showContextMenu();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        //if motion movement ends
        if ((event.getAction() == MotionEvent.ACTION_CANCEL) || (event.getAction() == MotionEvent.ACTION_UP)) {
            //if history is hidden
            if (prefs.getBoolean("history-hide", false) && prefs.getBoolean("history-onclick", false)) {
                //if not on the application list and not searching for something
                if ((kissBar.getVisibility() != View.VISIBLE) && (searchEditText.getText().toString().isEmpty())) {
                    //if list is empty
                    if ((this.list.getAdapter() == null) || (this.list.getAdapter().getCount() == 0)) {
                        searcher = new HistorySearcher(MainActivity.this);
                        searcher.execute();
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Clear text content when touching the cross button
     */
    @SuppressWarnings("UnusedParameters")
    public void onClearButtonClicked(View clearButton) {
        searchEditText.setText("");
    }

    /**
     * Display KISS menu
     */
    public void onLauncherButtonClicked(View launcherButton) {
        // Display or hide the kiss bar, according to current view tag (showMenu / hideMenu).

        displayKissBar(launcherButton.getTag().equals("showMenu"));
    }

    public void onFavoriteButtonClicked(View favorite) {

        // Favorites handling
        Pojo pojo = KissApplication.getDataHandler(MainActivity.this).getFavorites(tryToRetrieve)
                .get(Integer.parseInt((String) favorite.getTag()));
        final Result result = Result.fromPojo(MainActivity.this, pojo);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                result.fastLaunch(MainActivity.this);
            }
        }, KissApplication.TOUCH_DELAY);
    }

    private void displayClearOnInput() {
        final View clearButton = findViewById(R.id.clearButton);
        if (searchEditText.getText().length() > 0) {
            clearButton.setVisibility(View.VISIBLE);
            menuButton.setVisibility(View.INVISIBLE);
        } else {
            clearButton.setVisibility(View.INVISIBLE);
            menuButton.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void displayLoader(Boolean display) {
        final View loaderBar = findViewById(R.id.loaderBar);
        final View launcherButton = findViewById(R.id.launcherButton);

        int animationDuration = getResources().getInteger(
                android.R.integer.config_longAnimTime);

        if (!display) {
            launcherButton.setVisibility(View.VISIBLE);

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Animate transition from loader to launch button
                launcherButton.setAlpha(0);
                launcherButton.animate()
                        .alpha(1f)
                        .setDuration(animationDuration)
                        .setListener(null);
                loaderBar.animate()
                        .alpha(0f)
                        .setDuration(animationDuration)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                loaderBar.setVisibility(View.GONE);
                                loaderBar.setAlpha(1);
                            }
                        });
            } else {
                loaderBar.setVisibility(View.GONE);
            }
        } else {
            launcherButton.setVisibility(View.INVISIBLE);
            loaderBar.setVisibility(View.VISIBLE);
        }
    }

    private void displayKissBar(Boolean display) {
        final ImageView launcherButton = (ImageView) findViewById(R.id.launcherButton);

        // get the center for the clipping circle
        int cx = (launcherButton.getLeft() + launcherButton.getRight()) / 2;
        int cy = (launcherButton.getTop() + launcherButton.getBottom()) / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(kissBar.getWidth(), kissBar.getHeight());

        if (display) {
            // Display the app list
            if (searcher != null) {
                searcher.cancel(true);
            }
            searcher = new ApplicationsSearcher(MainActivity.this);
            searcher.execute();

            // Reveal the bar
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, 0, finalRadius);
                kissBar.setVisibility(View.VISIBLE);
                anim.start();
            } else {
                // No animation before Lollipop
                kissBar.setVisibility(View.VISIBLE);
            }

            // Retrieve favorites. Try to retrieve more, since some favorites can't be displayed (e.g. search queries)
            retrieveFavorites();

            hideKeyboard();
        } else {
            // Hide the bar
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, finalRadius, 0);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        kissBar.setVisibility(View.GONE);
                        super.onAnimationEnd(animation);
                    }
                });
                anim.start();
            } else {
                // No animation before Lollipop
                kissBar.setVisibility(View.GONE);
            }
            searchEditText.setText("");

            if (prefs.getBoolean("display-keyboard", false)) {
                // Display keyboard
                showKeyboard();
            }
        }
    }

    public void retrieveFavorites() {
        ArrayList<Pojo> favoritesPojo = KissApplication.getDataHandler(MainActivity.this)
                .getFavorites(tryToRetrieve);

        if (favoritesPojo.size() == 0) {
            int noFavCnt = prefs.getInt("no-favorites-tip", 0);
            if (noFavCnt<3) {
                Toast toast = Toast.makeText(MainActivity.this, getString(R.string.no_favorites), Toast.LENGTH_SHORT);
                toast.show();
                prefs.edit().putInt("no-favorites-tip", ++noFavCnt).commit();

            }
        }

        // Don't look for items after favIds length, we won't be able to display them
        for (int i = 0; i < Math.min(favsIds.length, favoritesPojo.size()); i++) {
            Pojo pojo = favoritesPojo.get(i);
            ImageView image = (ImageView) findViewById(favsIds[i]);

            Result result = Result.fromPojo(MainActivity.this, pojo);
            Drawable drawable = result.getDrawable(MainActivity.this);
            if (drawable != null)
                image.setImageDrawable(drawable);
            image.setVisibility(View.VISIBLE);
            image.setContentDescription(pojo.displayName);
        }

        // Hide empty favorites (not enough favorites yet)
        for (int i = favoritesPojo.size(); i < favsIds.length; i++) {
            findViewById(favsIds[i]).setVisibility(View.GONE);
        }
    }

    /**
     * This function gets called on changes. It will ask all the providers for
     * data
     *
     * @param query the query on which to search
     */

    private void updateRecords(String query) {
        if (searcher != null) {
            searcher.cancel(true);
        }

        if (query.length() == 0) {
            if (prefs.getBoolean("history-hide", false)) {
                searchEditText.setHint("");
                searcher = new NullSearcher(this);
                //Hide default scrollview
                findViewById(R.id.main_empty).setVisibility(View.INVISIBLE);

            } else {
                searchEditText.setHint(R.string.ui_search_hint);
                searcher = new HistorySearcher(this);
                //Show default scrollview
                findViewById(R.id.main_empty).setVisibility(View.VISIBLE);
            }
        } else {
            searcher = new QuerySearcher(this, query);
        }
        searcher.execute();
    }

    public void resetTask() {
        searcher = null;
    }

    /**
     * Call this function when we're leaving the activity We can't use
     * onPause(), since it may be called for a configuration change
     */
    public void launchOccurred(int index, Result result) {
        // We selected an item on the list,
        // now we can cleanup the filter:
        if (!searchEditText.getText().toString().equals("")) {
            searchEditText.setText("");
            hideKeyboard();
        }
    }

    @Override
    public void showKeyboard() {
        searchEditText.requestFocus();
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public int getFavIconsSize() {
        return favsIds.length;
    }
}

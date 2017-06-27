package com.arena.software.xand0;

import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.content.Context;

import android.content.Intent;

import android.view.MenuInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;

import android.preference.PreferenceManager;
import android.preference.PreferenceActivity;

import android.view.LayoutInflater;
import android.view.LayoutInflater.Factory;
import android.util.AttributeSet;
import android.os.Handler;
import android.view.InflateException;
import android.graphics.Color;

import java.lang.String;
import java.util.Random;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.purchase.InAppPurchase;

import com.facebook.FacebookSdk;

public class GameActivity extends AppCompatActivity {
    //
    // Store Cells IDs
    //
    private final int[][] m_CellIDs = new int[][]{
            {R.id.row1text1, R.id.row1text2, R.id.row1text3},
            {R.id.row2text1, R.id.row2text2, R.id.row2text3},
            {R.id.row3text1, R.id.row3text2, R.id.row3text3},
    };
    //
    // Store Cells content
    //
    private final CharSequence[][] m_CellsValues = new CharSequence[3][3];
    //
    //Default colors
    //
    private int red = 0xffff0000;
    private int green = 0xff008000;
    private int userColor = 0xff94f2e1;
    private int defaultColor = 0xff0099cc;
    //
    //Store difficulty
    //
    private CharSequence m_selectedDifficulty = "Medium";
    //
    //Store start options
    //
    private boolean m_computerStarts = false;
    //
    // Store shared preferences handler so we avoid creating new ones and losing performance
    //
    private SharedPreferences m_sharedPref;
    private SharedPreferences.Editor m_editor;
    //
    // Settings shared preferences handler
    //
    private SharedPreferences m_settingsSharedPref;
    //
    //Store results
    //
    private int m_win = 0;
    private int m_equal = 0;
    private int m_lose = 0;
    //
    //User and Computer marks
    //
    private CharSequence m_userMark = "X";
    private CharSequence m_computerMark = "0";

    private AdView mAdView;
    private InterstitialAd mInterstitialAd;

    private String interstitialAddId() {
        String str = "ca-app-pub-7089173816597376/4801884643";
        return str;
    }

    private String bannerAddId() {
        String str = "ca-app-pub-7089173816597376/7755351044";
        return str;
    }

    private String getAppID()
    {
        String str = "ca-app-pub-7089173816597376~4941485446";
        return str;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        //Initialize Mobile Ads
        MobileAds.initialize(getApplicationContext(), getAppID());

        //load Add
        mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        //load Interstitial Add
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId(interstitialAddId());

        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                requestNewInterstitial();
                openSettings();
            }
        });

        requestNewInterstitial();


        // Get Settings shared preference manager (Stores data from Settings Activity)
        m_settingsSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Get shared preferences manager
        m_sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        m_editor= m_sharedPref.edit();

        // When coming back from Settings Game activity is started again (and it was previously completly stopped
        // If any option was changed we will reset the current game
        // if we reset the current game there is no need to re-read data from settings (save some time and do not do that again)
        Boolean bGameWasReset = false;
        //Restore start options
        m_computerStarts = m_sharedPref.getBoolean(getUserStartsKey(), false);
        Boolean new_computerFirst = m_settingsSharedPref.getBoolean("computer_first_switch", false);
        if(!m_computerStarts == new_computerFirst)
        {
            //Computer start option was changed. We must start a new game and store the new value
            m_editor.putBoolean(getUserStartsKey(), new_computerFirst);
            m_editor.commit();
            m_computerStarts = new_computerFirst;

            ChooseMarks();
            reset();
            bGameWasReset = true;
        }
        else {
            //Choose marks order according to user Start option
            ChooseMarks();
        }
        //
        // Debug only - when needed delete all stored values
        // m_editor.clear().commit();
        //
        // Difficulty
        // Get old value, get new value
        // Compare and store new value if it has changed
        //
        m_selectedDifficulty = m_sharedPref.getString(getDifficultyKey(), getRadioMedium());
        String newDifficulty = m_settingsSharedPref.getString("difficulty_list", getRadioMedium());
        if (!m_selectedDifficulty.equals(newDifficulty)) {
            m_editor.putString(getDifficultyKey(), newDifficulty.toString());
            m_editor.commit();
            m_selectedDifficulty = newDifficulty;

            //Difficulty was changed. We must start a new game and store the new value
            if(!bGameWasReset)
                reset(); //else game was already reset
            bGameWasReset = true;
        }

        // Reset scores is default false
        m_settingsSharedPref.edit().putBoolean("reset_scores", false).commit();

        //
        // Read scores
        m_win = Integer.parseInt(m_settingsSharedPref.getString(getWinScoreKey(), "0"));
        m_equal = Integer.parseInt(m_settingsSharedPref.getString(getEqualMatchScoreKey(), "0"));
        m_lose = Integer.parseInt(m_settingsSharedPref.getString(getLoseScoreKey(), "0"));

        if(!bGameWasReset) {
            // Get previous cell values
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++) {
                    TextView mTextView = (TextView) findViewById(m_CellIDs[i][j]);
                    //Content
                    String lastCellValue = m_sharedPref.getString(getCellContentKey(i, j), "");
                    mTextView.setText(lastCellValue);

                    //Update cell values
                    m_CellsValues[i][j] = lastCellValue;

                    //Color
                    int lastCellColor = m_sharedPref.getInt(getCellColorKey(i, j), defaultColor);
                    mTextView.setTextColor(lastCellColor);

                    //isClickable
                    boolean areCellsClickable = m_sharedPref.getBoolean(getCellClickableKey(i, j), true);
                    mTextView.setClickable(areCellsClickable);
                }
            //Restore welcome Text View
            TextView welcomeTextView = (TextView) findViewById(R.id.welcomeText);
            //Content
            String lastWelcomeValue = m_sharedPref.getString(getWelcomeLabelKey(), getWelcomeText());
            welcomeTextView.setText(lastWelcomeValue);
            //Color
            int lastWelcomeColor = m_sharedPref.getInt(getWelcomeLabelColorKey(), defaultColor);
            welcomeTextView.setTextColor(lastWelcomeColor);
        }
        //
        //If user starts do nothing, else start with a computer move
        //
        if (m_computerStarts) {
            //Computer Starts only if the game isn't already started
            if (areAllCellsEmpty())
                randomComputerMove();
        }
    }
    private void requestNewInterstitial() {
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .build();

        mInterstitialAd.loadAd(adRequest);
    }


    private void ChooseMarks() {
        if (m_computerStarts) {
            m_computerMark = getFirstMark();
            m_userMark = getSecondMark();
        } else
        {
            m_userMark = getFirstMark();
            m_computerMark = getSecondMark();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // show menu when menu button is pressed
       MenuInflater inflater = getMenuInflater();
       inflater.inflate(R.menu.xand0_menu, menu);

        return true;
    }

    public void openSettings()
    {
        Intent j = new Intent(this, SettingsActivity.class);
        startActivity(j);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // display a message when a button was pressed
        String message = "";
        if (item.getItemId() == R.id.Options) {

            // open Interstitial add when options are selected
            //
            //
            if (mInterstitialAd.isLoaded()) {
                mInterstitialAd.show();
            } else {
               openSettings();
            }
        }
        else if (item.getItemId() == R.id.About) {
            Intent j = new Intent(this, AboutActivity.class);
            startActivity(j);
        }
        else if (item.getItemId() == R.id.InAppPurchase) {
            Intent j = new Intent(this, InAppPurchaseActivity.class);
            startActivity(j);
        }
        else
            if(item.getItemId() == R.id.Rate)
            {
                Uri uri = Uri.parse("market://details?id=" + getApplicationContext().getPackageName());
                Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                // To count with Play market backstack, After pressing back button,
                // to taken back to our application, we need to add following flags to intent.
                goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                try {
                    startActivity(goToMarket);
                } catch (ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://play.google.com/store/apps/details?id=" + getApplicationContext().getPackageName())));
                }
            }
      /*  if (item.getItemId() == R.id.settings) {
            Intent j = new Intent(this, SettingsActivity.class);
            j.putExtra( PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.SettingsPreferenceFragment.class.getName() );
            j.putExtra( PreferenceActivity.EXTRA_NO_HEADERS, true );
            startActivity(j);
            //message = "You selected settings!";
        }
        else if (item.getItemId() == R.id.statistics) {
            Intent j = new Intent(this, SettingsActivity.class);
            j.putExtra( PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.ScoresPreferenceFragment.class.getName() );
            j.putExtra( PreferenceActivity.EXTRA_NO_HEADERS, true );
            startActivity(j);
            //message = "You selected statistics";
        }*/
        else {
            message = "Why would you select that!?";
        }

        // show message via toast
        // Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        // toast.show();

        return true;
    }
    @NonNull
    private String getLoseKey() {
        return "lose";
    }

    @NonNull
    private String getEqualKey() {
        return "equal";
    }

    @NonNull
    private String getWinKey() {
        return "win";
    }

    @NonNull
    private String getUserStartsKey() {
        return "userStarts";
    }

    @NonNull
    private String getDifficultyKey() {
        return "difficulty";
    }
    private String getCellClickableKey(int i, int j) {
        return "cell" + i + j + "Clickable";
    }

    @NonNull
    private String getCellContentKey(int i, int j) {
        return "cell" + i + j;
    }
    @NonNull
    private String getCellColorKey(int i, int j) {
        return "cell" + i + j + "Color";
    }
    @NonNull
    private String getWelcomeLabelColorKey() {
        return "welcomeLabelColor";
    }
    private String getWinScoreKey()
    {
        return getResources().getText(R.string.win_score).toString();
    }
    private String getEqualMatchScoreKey()
    {
        return getResources().getText(R.string.equal_match_score).toString();
    }
    private String getLoseScoreKey()
    {
        return getResources().getText(R.string.lose_score).toString();
    }
    // Keys used to store preferences
    @NonNull
    private String getWelcomeLabelKey() {
        return "welcomeLabel";
    }
    private CharSequence getFirstMark()
    {
        return "X";
    }
    private CharSequence getSecondMark()
    {
        return "0";
    }
    //
    // Get texts from Resources - String file
    //
    @NonNull
    private String getWelcomeText()
    {
        return getResources().getText(R.string.welcome_string).toString();
    }
    @NonNull
    private String getRadioEasy()
    {
        return getResources().getText(R.string.radio_easy).toString();
    }
    @NonNull
    private String getRadioMedium()
    {
        return getResources().getText(R.string.radio_medium).toString();
    }
    @NonNull
    private String getRadioHard()
    {
        return getResources().getText(R.string.radio_hard).toString();
    }
    @NonNull
    private String getEqualMatch()
    {
        return getResources().getText(R.string.equal_match).toString();
    }
    @NonNull
    private String getLose()
    {
        return getResources().getText(R.string.lose).toString();
    }
    @NonNull
    private String getWin()
    {
        return getResources().getText(R.string.win).toString();
    }
    public void onTextViewClick(View v) {
        //
        // User move is allowed only if cell is empty, else nothing happens, user has to press another cell
        //
        boolean bUserMoved = false;
        switch (v.getId()) {
            case R.id.row1text1:
                if (m_CellsValues[0][0].length() == 0) {
                    userMove(0, 0);
                    bUserMoved = true;
                }
                break;
            case R.id.row1text2:
                if (m_CellsValues[0][1].length() == 0) {
                    userMove(0, 1);
                    bUserMoved = true;
                }
                break;
            case R.id.row1text3:
                if (m_CellsValues[0][2].length() == 0) {
                    userMove(0, 2);
                    bUserMoved = true;
                }
                break;
            case R.id.row2text1:
                if (m_CellsValues[1][0].length() == 0) {
                    userMove(1, 0);
                    bUserMoved = true;
                }
                break;
            case R.id.row2text2:
                if (m_CellsValues[1][1].length() == 0) {
                    userMove(1, 1);
                    bUserMoved = true;
                }
                break;
            case R.id.row2text3:
                if (m_CellsValues[1][2].length() == 0) {
                    userMove(1, 2);
                    bUserMoved = true;
                }
                break;
            case R.id.row3text1:
                if (m_CellsValues[2][0].length() == 0) {
                    userMove(2, 0);
                    bUserMoved = true;
                }
                break;
            case R.id.row3text2:
                if (m_CellsValues[2][1].length() == 0) {
                    userMove(2, 1);
                    bUserMoved = true;
                }
                break;
            case R.id.row3text3:
                if (m_CellsValues[2][2].length() == 0) {
                    userMove(2, 2);
                    bUserMoved = true;
                }
                break;
            default:
                break;
        }

        TextView textViewWelcome = (TextView) findViewById(R.id.welcomeText);
        if(bUserMoved) {
            //
            // If all cells are now complete, now more computer turn
            // Check if user won or not
            //
            boolean bUserWon = isGameOver(m_userMark, green);
            if (!bUserWon) {
                if (areAllCellsFull()) {
                    //EqualMatch
                    closeGame(getEqualKey());
                    textViewWelcome.setText(getEqualMatch());
                    saveWelcomeLabel();
                }
                else {
                    computerTurn();
                    //
                    // Check if computer won
                    //
                    boolean bComputerWon = isGameOver(m_computerMark, red);
                    if (bComputerWon) {
                        closeGame(getLoseKey());
                        textViewWelcome.setTextColor(red);
                        textViewWelcome.setText(getLose());
                        saveWelcomeLabel();
                    } else {
                        if (areAllCellsFull()) {
                            closeGame(getEqualKey());
                            textViewWelcome.setText(getEqualMatch());
                            saveWelcomeLabel();
                        }
                    }
                }
            } else {
                closeGame(getWinKey());
                textViewWelcome.setTextColor(green);
                textViewWelcome.setText(getWin());
                saveWelcomeLabel();
            }
        }
    }

    private boolean areAllCellsFull()
    {
        int nr = 0;
        for (int i = 0; i < 3; i++)
        for(int j = 0; j < 3; j++){
            TextView textView = (TextView) findViewById(m_CellIDs[i][j]);
            if(textView.getText().length() != 0)
                nr++;
        }
        if(nr == 9)
            return true;
        else return false;
    }
    private boolean areAllCellsEmpty()
    {
        for (int i = 0; i < 3; i++)
            for(int j = 0; j < 3; j++){
                TextView textView = (TextView) findViewById(m_CellIDs[i][j]);
                if(textView.getText().length() != 0)
                    return false;
            }
      return true;
    }
    private void closeGame(String resolution) {
        //When we close the game we make all cells not clickable
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                TextView textView = (TextView) findViewById(m_CellIDs[i][j]);
                textView.setClickable(false);
                saveClickable(i, j);
            }


       if(resolution.equals(getEqualKey())) {
           m_equal++;
           m_settingsSharedPref.edit().putString(getEqualMatchScoreKey(), Integer.toString(m_equal)).commit();
       }
        else
           if(resolution.equals(getWinKey()))
           {
               m_win++;
               m_settingsSharedPref.edit().putString(getWinScoreKey(),Integer.toString(m_win)).commit();
           }
        else
           if(resolution.equals(getLoseKey()))
           {
               m_lose++;
               m_settingsSharedPref.edit().putString(getLoseScoreKey(), Integer.toString(m_lose)).commit();
           }
        else
           {
               //we should never get here
               assert(false);
           }
        m_editor.commit();
    }
    private void saveWelcomeLabel()
    {
        TextView textView = (TextView) findViewById(R.id.welcomeText);
        m_editor.putString(getWelcomeLabelKey(), textView.getText().toString());
        m_editor.putInt(getWelcomeLabelColorKey(), textView.getCurrentTextColor());
        m_editor.commit();
    }
    private void saveCell(int i, int j) {
        //Save Preferences so they are kept if app is minimized or closed - Trello Bug 3
        String cellKey = getCellContentKey(i, j);
        String cellColorKey = getCellColorKey(i, j);

        TextView currTextView = (TextView) findViewById(m_CellIDs[i][j]);
        m_editor.putString(cellKey, m_CellsValues[i][j].toString());
        m_editor.putInt(cellColorKey, currTextView.getCurrentTextColor());
        m_editor.commit();
    }

    private void saveClickable(int i, int j) {
        TextView currTextView = (TextView) findViewById(m_CellIDs[i][j]);
        m_editor.putBoolean(getCellClickableKey(i, j), currTextView.isClickable());
        m_editor.commit();
    }

    private void userMove(int i, int j) {
        TextView mTextView = (TextView) findViewById(m_CellIDs[i][j]);
        mTextView.setText(m_userMark);
        mTextView.setTextColor(userColor);

        m_CellsValues[i][j] = m_userMark;

        saveCell(i, j);
    }
    private void computerMove(int i, int j) {
        TextView mTextView = (TextView) findViewById(m_CellIDs[i][j]);
        mTextView.setText(m_computerMark);

        m_CellsValues[i][j] = m_computerMark;

        saveCell(i, j);
    }
    private void saveGameResolution(int i, int j, int color) {
        TextView mTextView = (TextView) findViewById(m_CellIDs[i][j]);
        mTextView.setTextColor(color);

        //Save color
        String cellColorKey = getCellColorKey(i, j);
        m_editor.putInt(cellColorKey, mTextView.getCurrentTextColor());
        m_editor.commit();
    }
    //
    // Check if game is over - 0 for computer, X for user
    //
    private boolean isGameOver(CharSequence mark, int color)
    {
        boolean bIsOver = false;
        //
        // Check lines
        //
        for(int i = 0; i < 3; i++) {
            if (m_CellsValues[i][0].equals(mark) && m_CellsValues[i][1].equals(mark) && m_CellsValues[i][2].equals(mark)) {
                bIsOver = true;
                saveGameResolution(i, 0, color);
                saveGameResolution(i, 1, color);
                saveGameResolution(i, 2, color);
            }
        }
        //
        // Check columns
        //
        for(int i = 0; i < 3; i++)
        {
            if (m_CellsValues[0][i].equals(mark) && m_CellsValues[1][i].equals(mark) && m_CellsValues[2][i].equals(mark)) {
                bIsOver = true;
                saveGameResolution(0, i, color);
                saveGameResolution(1, i, color);
                saveGameResolution(2, i, color);
            }
        }

        // Check diagonals
        if(m_CellsValues[0][0].equals(mark) && m_CellsValues[1][1].equals(mark) && m_CellsValues[2][2].equals(mark))
        {
            bIsOver = true;
            saveGameResolution(0, 0, color);
            saveGameResolution(1, 1, color);
            saveGameResolution(2, 2, color);
        }
        if(m_CellsValues[0][2].equals(mark) && m_CellsValues[1][1].equals(mark) && m_CellsValues[2][0].equals(mark))
        {
            bIsOver = true;
            saveGameResolution(0, 2, color);
            saveGameResolution(1, 1, color);
            saveGameResolution(2, 0, color);
        }
        return bIsOver;
    }
    private boolean checkIfRowIsAlmostComplete(int i, CharSequence mark)
    {
        boolean bRet = false;
        if (m_CellsValues[i][0].equals(mark) && m_CellsValues[i][1].equals(mark) && m_CellsValues[i][2].length() == 0) {
            computerMove(i, 2);
            bRet = true;
        } else if (m_CellsValues[i][0].equals(mark) && m_CellsValues[i][2].equals(mark) && m_CellsValues[i][1].length() == 0) {
            computerMove(i,1);
            bRet = true;
        } else if (m_CellsValues[i][2].equals(mark) && m_CellsValues[i][1].equals(mark) && m_CellsValues[i][0].length() == 0) {
            computerMove(i, 0);
            bRet = true;
        }
        return bRet;
    }
    private boolean checkIfColumnIsAlmostComplete(int i, CharSequence mark)
    {
        boolean bRet = false;
        if (m_CellsValues[0][i].equals(mark) && m_CellsValues[1][i].equals(mark) && m_CellsValues[2][i].length() == 0) {
            computerMove(2, i);
            bRet = true;
        }
        else if (m_CellsValues[0][i].equals(mark) && m_CellsValues[2][i].equals(mark) && m_CellsValues[1][i].length() == 0) {
            computerMove(1, i);
            bRet = true;
        }
        else if (m_CellsValues[2][i].equals(mark) && m_CellsValues[1][i].equals(mark) && m_CellsValues[0][i].length() == 0) {
            computerMove(0, i);
            bRet = true;
        }
        return bRet;
    }
    private boolean checkIfDiagonalsAreAlmostComplete(CharSequence mark)
    {
        boolean bRet = false;
        //
        // First diagonal
        //
        if (m_CellsValues[0][0].equals(mark) && m_CellsValues[1][1].equals(mark) && m_CellsValues[2][2].length() == 0) {
            computerMove(2, 2);
            bRet = true;
        }
        else if (m_CellsValues[0][0].equals(mark) && m_CellsValues[2][2].equals(mark) && m_CellsValues[1][1].length() == 0) {
            computerMove(1, 1);
            bRet = true;
        }
        else if (m_CellsValues[1][1].equals(mark) && m_CellsValues[2][2].equals(mark) && m_CellsValues[0][0].length() == 0) {
            computerMove(0, 0);
            bRet = true;
        }
        //
        // Second diagonal
        //
        if(!bRet) {
            if (m_CellsValues[0][2].equals(mark) && m_CellsValues[1][1].equals(mark) && m_CellsValues[2][0].length() == 0) {
                computerMove(2, 0);
                bRet = true;
            } else if (m_CellsValues[0][2].equals(mark) && m_CellsValues[2][0].equals(mark) && m_CellsValues[1][1].length() == 0) {
                computerMove(1, 1);
                bRet = true;
            } else if (m_CellsValues[1][1].equals(mark) && m_CellsValues[2][0].equals(mark) && m_CellsValues[0][2].length() == 0) {
                computerMove(0, 2);
                bRet = true;
            }
        }
        return bRet;
    }
    public void computerTurn()
    {
        //
        //First check if game can be closed
        //
        boolean bComputerMoved = false;
        for(int i = 0; i < 3; i++) {
            bComputerMoved = checkIfRowIsAlmostComplete(i, m_computerMark);
            if(bComputerMoved)
                break;
        }

        if(!bComputerMoved) {
            for (int i = 0; i < 3; i++) {
                bComputerMoved = checkIfColumnIsAlmostComplete(i, m_computerMark);
                if(bComputerMoved)
                    break;
            }
        }

        if(!bComputerMoved)
            bComputerMoved = checkIfDiagonalsAreAlmostComplete(m_computerMark);
        //
        // Then check if user attack must be blocked
        //
        if(!bComputerMoved) {
            for (int i = 0; i < 3; i++) {
                bComputerMoved = checkIfRowIsAlmostComplete(i, m_userMark);
                if(bComputerMoved)
                    break;
            }
        }

        if (!bComputerMoved) {
            for (int i = 0; i < 3; i++) {
                bComputerMoved = checkIfColumnIsAlmostComplete(i, m_userMark);
                if(bComputerMoved)
                    break;
            }
        }

        if (!bComputerMoved) {
            bComputerMoved = checkIfDiagonalsAreAlmostComplete(m_userMark);
        }

        // After checking if game can be closed or attack must be combated, choose move based on difficulty

        if (!bComputerMoved) {
            if (m_selectedDifficulty.equals(getRadioHard())) {
                //Middle first
                if (m_CellsValues[1][1].length() == 0)
                    computerMove(1, 1);
                else {
                    //Treat the special case that allows to win the game - fill two cells on the diagonal (see trello - Bug1 for more info)
                    if ((m_CellsValues[0][0].equals(m_userMark) && m_CellsValues[2][2].equals(m_userMark)) || (m_CellsValues[0][2].equals(m_userMark) && m_CellsValues[2][0].equals(m_userMark)))
                        moveSides();
                        // Trello Bug 4 - Special case: user moves in the two cells next to down left cell
                    else if (m_CellsValues[2][1].equals(m_userMark) && m_CellsValues[1][2].equals(m_userMark) && m_CellsValues[2][2].length() == 0)
                        computerMove(2, 2);
                        //Trello Bug 5 - special cases - L forms
                    else if (m_CellsValues[2][0].equals(m_userMark) && m_CellsValues[1][2].equals(m_userMark) && m_CellsValues[2][2].length() == 0)
                        computerMove(2, 2);
                    else if (m_CellsValues[2][1].equals(m_userMark) && m_CellsValues[0][2].equals(m_userMark) && m_CellsValues[2][2].length() == 0)
                        computerMove(2, 2);
                    else if (m_CellsValues[0][0].equals(m_userMark) && m_CellsValues[2][1].equals(m_userMark) && m_CellsValues[2][0].length() == 0)
                        computerMove(2, 0);
                        // Treat corners
                    else if (m_CellsValues[0][0].length() == 0)
                        computerMove(0, 0);
                    else if (m_CellsValues[0][2].length() == 0)
                        computerMove(0, 2);
                    else if (m_CellsValues[2][0].length() == 0)
                        computerMove(2, 0);
                    else if (m_CellsValues[2][2].length() == 0)
                        computerMove(2, 2);
                        //Treat other remaining cells
                    else
                        moveSides();
                }

            } else if (m_selectedDifficulty.equals(getRadioMedium())) {
                // Medium equals middle and then corners
                if (m_CellsValues[1][1].length() == 0)
                    computerMove(1, 1);
                else if (m_CellsValues[0][0].length() == 0)
                    computerMove(0, 0);
                else if (m_CellsValues[0][2].length() == 0)
                    computerMove(0, 2);
                else if (m_CellsValues[2][0].length() == 0)
                    computerMove(2, 0);
                else if (m_CellsValues[2][2].length() == 0)
                    computerMove(2, 2);
                    //Treat other remaining cells
                else
                    moveSides();
            } else {
                //Easy means random cells
                bComputerMoved = randomComputerMove();
            }
        }
    }

    public void moveSides() {
        if (m_CellsValues[0][1].length() == 0)
            computerMove(0, 1);
        else if (m_CellsValues[1][0].length() == 0)
            computerMove(1, 0);
        else if (m_CellsValues[1][2].length() == 0)
            computerMove(1, 2);
        else if (m_CellsValues[2][1].length() == 0)
            computerMove(2, 1);
    }
    public boolean randomComputerMove()
    {
        boolean bMoved = false;
        //Computer starts randomly
        Random rand = new Random();
        while(!bMoved) {
            int nextMoveLine = rand.nextInt(3);
            int nextMoveColl = rand.nextInt(3);
            if (m_CellsValues[nextMoveLine][nextMoveColl].length() == 0)  // our Random generated value is empty = a valid move
            {
                computerMove(nextMoveLine, nextMoveColl);
                bMoved = true;
            }
        }
        return bMoved;
    }
    public void reset()
    {
        for (int i = 0; i < 3; i++)
        for(int j = 0; j < 3; j++){
            TextView textView = (TextView) findViewById(m_CellIDs[i][j]);
            textView.setText("");
            textView.setClickable(true);
            textView.setTextColor(defaultColor);

            m_CellsValues[i][j] = "";

            saveCell(i, j);
            saveClickable(i, j);
        }
        TextView welcomeTextView = (TextView) findViewById(R.id.welcomeText);
        welcomeTextView.setTextColor(defaultColor);
        welcomeTextView.setText(getWelcomeText());
        saveWelcomeLabel();

        //
        // If computer must start perform one move
        //
        if(m_computerStarts)
            randomComputerMove();
    }
    public void newGame(View v)
    {
        reset();
    }
}

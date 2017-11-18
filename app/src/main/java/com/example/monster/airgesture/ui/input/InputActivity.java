package com.example.monster.airgesture.ui.input;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.monster.airgesture.Conditions;
import com.example.monster.airgesture.R;
import com.example.monster.airgesture.data.WordQueryImpl;
import com.example.monster.airgesture.data.bean.CandidateWord;
import com.example.monster.airgesture.data.bean.Word;
import com.example.monster.airgesture.timer.TimerHelper;
import com.example.monster.airgesture.timer.TimerProcessor;
import com.example.monster.airgesture.ui.test.MainActivity;
import com.example.monster.airgesture.ui.base.BaseActivity;
import com.example.monster.airgesture.utils.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责展示数据的View层，处理数据的展示{@link InputContract.View}
 * 主要交互逻辑见onClick方法{@link InputActivity#onClick(View)}
 * 手势识别和数据库的交互见对应的presenter实现类{@link InputPresenterImpl}
 * Created by WelkinShadow on 2017/10/26.
 */

public class InputActivity<T extends InputContract.Presenter> extends BaseActivity<T> implements
        InputContract.View, View.OnClickListener {

    private static final String TAG = "InputActivity";

    private EditText inputtedArea;
    private TextView inputStrokes;
    private RecyclerView candidateWordArea;
    private int[] buttons = {R.id.bt_on, R.id.bt_off, R.id.bt_del, R.id.bt_clear,
            R.id.bt_space, R.id.bt_num, R.id.bt_comma, R.id.bt_period};
    private Button capLocks;

    private TimerHelper timerHelper;

    //自动输入定时时间
    private final int AUTO_INPUT_MILLI = 1500;

    private boolean isOn = false;
    private boolean isNumKeyboard = false;
    private boolean isTouchRecycler = false;
    private boolean isTiming = false;

    //大小写状态位
    private int capStatus = 102;
    private final int FIRST_CAP = 100;
    private final int ALL_CAP = 101;
    private final int NO_CAP = 102;

    private WordAdapter<Word> candidateWordAdapter = null;

    @Override
    @SuppressWarnings("unchecked")
    public T setPresenter() {
        return (T) new InputPresenterImpl();
    }

    @Override
    public int setLayout() {
        return R.layout.layout_main;
    }

    @Override
    protected int getMenuId() {
        return R.menu.menu_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //申请权限拷贝模板数据
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        getPresenter().initConfig();
        getPresenter().attachQueryModel(new WordQueryImpl());
        timerHelper = new TimerHelper(AUTO_INPUT_MILLI, new TimerProcessor() {
            @Override
            public void process() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Word word = candidateWordAdapter.getFirst();
                        if (word != null && word instanceof CandidateWord
                                && !isNumKeyboard && !isTouchRecycler) {
                            isTiming = false;
                            enterWord(candidateWordAdapter.getFirst().getWord());
                        }
                    }
                });
            }
        });
    }

    @Override
    @SuppressWarnings("all")
    public void initViews() {
        inputStrokes = findView(R.id.input_strokes);
        inputtedArea = findView(R.id.inputted_area);
        inputtedArea.setOnClickListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        layoutManager.setSmoothScrollbarEnabled(true);

        candidateWordArea = findView(R.id.candidate_word);
        candidateWordArea.setLayoutManager(layoutManager);
        candidateWordAdapter = new WordAdapter(new ArrayList(), new WordAdapter.OnItemClickListener() {
            @Override
            public void onClickItem(Word word) {
                enterWord(word.getWord());
            }

            @Override
            public void onLongClickItem(Word word) {
            }
        });
        candidateWordArea.setAdapter(candidateWordAdapter);
        candidateWordArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        isTouchRecycler = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        isTouchRecycler = false;
                        setWordByAuto();
                        break;
                }
                return false;
            }
        });

        Button bt = null;
        for (int id : buttons) {
            bt = findView(id);
            bt.setOnClickListener(this);
        }
        bt = findView(R.id.bt_off);
        bt.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));

        capLocks = findView(R.id.bt_caplock);
        capLocks.setText(getString(R.string.no_cap));
        capLocks.setOnClickListener(this);
    }

    /**
     * 删除单词
     */
    public void delWord() {
        Log.i(TAG, "delete word");
        String text = inputtedArea.getText().toString();
        //删除最后一个单词
        int lastIndex = text.lastIndexOf(" ");
        if (lastIndex == -1) {
            clearInput();
        } else {
            text = text.substring(0, text.lastIndexOf(" "));
            inputtedArea.setText(text);
            inputtedArea.setSelection(text.length());
        }

        //删除最后一个字符
        /*text = text.substring(0,text.length()-1);*/

    }

    /**
     * 单词输入
     */
    @Override
    public void enterWord(String word) {
        Log.i(TAG, "input word :" + word);
        resetCapLock();
        String text = inputtedArea.getText().toString();
        String appendText = text.length() > 0 && !isNumKeyboard ? " " + word : word;
        inputtedArea.append(appendText);
        //数字键盘不需要清空和查找关联词
        if (!isNumKeyboard) {
            clearStroke();
            clearCandidateWord();
            getPresenter().findContacted(word);
            Log.i(TAG, "find contacted");
        }
    }

    /**
     * 清空输入区
     */
    private void clearInput() {
        Log.i(TAG, "clear input");
        inputtedArea.setText("");
    }

    /**
     * 删除笔画
     */
    @Override
    public void setStroke(int type) {
        Log.i(TAG, "set stroke");
        //返回对应type的Stokes字符
        inputStrokes.append(Conditions.STOKES[type - 1]);
    }

    /**
     * 删除笔画
     */
    private void deleteStroke() {
        Log.i(TAG, "delete stroke");
        String strokes = inputStrokes.getText().toString();
        if (strokes.length() > 0) {
            inputStrokes.setText(strokes.subSequence(0, strokes.length() - 1));
            getPresenter().delStoker();
            if (strokes.length() == 1) {
                clearCandidateWord();
            }
        }
    }

    /**
     * 清空笔画区
     */
    private void clearStroke() {
        Log.i(TAG, "clear stroke");
        inputStrokes.setText("");
        getPresenter().clearStoker();
    }

    /**
     * 设置候选词
     */
    @Override
    @SuppressWarnings("unchecked")
    public void setCandidateWord(List<Word> words) {
        Log.i(TAG, "set candidate word");
        candidateWordAdapter.notifyDiff(words);
        setWordByAuto();
    }

    private void setWordByAuto() {
        cancelCurrentTimerTask();
        timerHelper.startTimer();
        isTiming = true;
    }

    private void cancelCurrentTimerTask() {
        if (isTiming) {
            timerHelper.stopTimer();
            isTiming = false;
        }
    }

    /**
     * 清空候选词区
     */
    @Override
    public void clearCandidateWord() {
        Log.i(TAG, "clear candidateWords");
        if (candidateWordAdapter != null) {
            candidateWordAdapter.notifyDiff(new ArrayList<Word>());
        }
    }

    /**
     * 大小写转换
     */
    private void transformCaplock() {
        Log.i(TAG, "transform caplocks");
        String text = inputtedArea.getText().toString();
        int lastWordIndex = text.lastIndexOf(" ") == -1 ? 0 : text.lastIndexOf(" ") + 1;
        String lastWord = text.substring(lastWordIndex, text.length());
        String afterTransform = null;
        capStatus = capStatus == NO_CAP ? FIRST_CAP : capStatus + 1;
        switch (capStatus) {
            case NO_CAP:
                afterTransform = StringUtil.transformNoCapsAll(lastWord);
                capLocks.setTextColor(ContextCompat.getColor(this, R.color.black));
                capLocks.setText(getString(R.string.no_cap));
                break;
            case FIRST_CAP:
                afterTransform = StringUtil.transformCapsFirst(lastWord);
                capLocks.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
                capLocks.setText(getString(R.string.first_cap));
                break;
            case ALL_CAP:
                afterTransform = StringUtil.transformCapsAll(lastWord);
                capLocks.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
                capLocks.setText(getString(R.string.all_cap));
                break;
        }
        afterTransform = text.substring(0, lastWordIndex) + afterTransform;
        inputtedArea.setText(afterTransform);
        inputtedArea.setSelection(afterTransform.length());
    }

    /**
     * 置位大小写锁
     */
    private void resetCapLock() {
        capStatus = NO_CAP;
        capLocks.setTextColor(ContextCompat.getColor(this, R.color.black));
        capLocks.setText(getString(R.string.no_cap));
    }

    @Override
    public void onClick(View view) {
        String strokeText = inputStrokes.getText().toString();
        String inputText = inputtedArea.getText().toString();
        Button bt;
        switch (view.getId()) {
            //开启识别
            case R.id.bt_on:
                if (!isOn) {
                    bt = findView(R.id.bt_on);
                    bt.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
                    bt = findView(R.id.bt_off);
                    bt.setTextColor(ContextCompat.getColor(this, R.color.black));
                    getPresenter().startRecording();
                    showMessage("开始读取手势");
                    isOn = true;
                } else {
                    showMessage("已经开启手势读取");
                }
                break;

            //关闭识别
            case R.id.bt_off:
                if (isOn) {
                    bt = findView(R.id.bt_on);
                    bt.setTextColor(ContextCompat.getColor(this, R.color.black));
                    bt = findView(R.id.bt_off);
                    bt.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
                    getPresenter().stopRecording();
                    showMessage("关闭识别功能");
                    isOn = false;
                } else {
                    showMessage("已经关闭");
                }
                break;

            //大小写切换
            case R.id.bt_caplock:
                transformCaplock();
                break;

            //clear按钮
            case R.id.bt_clear:
                if (strokeText.length() > 0) {
                    clearStroke();
                } else if (inputText.length() > 0) {
                    clearInput();
                }
                showMessage("已清空");
                break;

            case R.id.bt_comma:
                enterWord(",");
                break;

            case R.id.bt_period:
                enterWord(".");
                break;

            //删除按钮
            case R.id.bt_del:
                if (strokeText.length() > 0) {
                    deleteStroke();
                } else if (inputText.length() > 0) {
                    delWord();
                } else {
                    showMessage("已清空");
                }
                break;

            //数字键盘
            case R.id.bt_num:
                bt = (Button) findViewById(R.id.bt_num);
                if (isNumKeyboard) {
                    bt.setTextColor(ContextCompat.getColor(this, R.color.black));
                } else {
                    bt.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
                }
                clearStroke();
                getPresenter().changeNumKeyboard();
                isNumKeyboard = !isNumKeyboard;
                break;

            case R.id.bt_space:
                enterWord(" ");
                break;

            case R.id.inputted_area:
                resetCapLock();
                break;

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showMessage("拒绝访问sd卡将导致无法识别动作数据");
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_test) {
            //跳转到InputActivity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
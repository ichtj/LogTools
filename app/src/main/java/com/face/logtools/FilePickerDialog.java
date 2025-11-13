// FilePickerDialog.java
package com.face.logtools;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilePickerDialog extends Dialog {

    public interface OnPathSelectedListener {
        void onPathSelected(String path);
        void onCancel();
    }

    private Context mContext;
    private String mCurrentPath;
    private OnPathSelectedListener mListener;
    private String mTitle;

    // UI组件
    private TextView tvTitle;
    private TextView tvCurrentPath;
    private ScrollView scrollView;
    private LinearLayout itemContainer;
    private Button btnConfirm;
    private Button btnCancel;

    // 线程处理
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 选中状态
    private String selectedPath = null;
    private FileItemView selectedItemView = null;

    public FilePickerDialog(Context context, String title, String initialPath) {
        super(context);
        this.mContext = context;
        this.mTitle = title;
        this.mCurrentPath = TextUtils.isEmpty(initialPath) ? "/" : initialPath;
        initDialog();
        createViews();
        loadDirectory(mCurrentPath);
    }

    private void initDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        setCancelable(true);
        setCanceledOnTouchOutside(false);
    }

    private void createViews() {
        // 主容器
        LinearLayout mainLayout = new LinearLayout(mContext);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.WHITE);
        mainLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // 设置圆角背景
        mainLayout.setBackground(createRoundedBackground());

        // 标题
        tvTitle = new TextView(mContext);
        tvTitle.setText(mTitle);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvTitle.setTextColor(Color.BLACK);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dpToPx(16));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mainLayout.addView(tvTitle, titleParams);

        // 当前路径显示
        tvCurrentPath = new TextView(mContext);
        tvCurrentPath.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvCurrentPath.setTextColor(Color.GRAY);
        tvCurrentPath.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        tvCurrentPath.setBackgroundColor(0xFFF5F5F5);
        tvCurrentPath.setSingleLine(false);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pathParams.setMargins(0, 0, 0, dpToPx(12));
        mainLayout.addView(tvCurrentPath, pathParams);

        // 文件列表容器
        itemContainer = new LinearLayout(mContext);
        itemContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView = new ScrollView(mContext);
        scrollView.addView(itemContainer);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(300)
        );
        scrollParams.setMargins(0, 0, 0, dpToPx(16));
        mainLayout.addView(scrollView, scrollParams);

        // 按钮容器
        LinearLayout buttonLayout = new LinearLayout(mContext);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END);

        // 取消按钮
        btnCancel = createButton("取消", Color.GRAY);
        btnCancel.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onCancel();
            }
            dismiss();
        });

        // 确认按钮
        btnConfirm = createButton("确认", 0xFF2196F3);
        btnConfirm.setOnClickListener(v -> {
            if (selectedPath != null && mListener != null) {
                if (new File(selectedPath).isFile()) {
                    Toast.makeText (mContext,"请选择文件夹路径,不能选择文件!",Toast.LENGTH_SHORT).show ();
                    return;
                }
                mListener.onPathSelected(selectedPath);
            } else if (mListener != null) {
                if (new File(mCurrentPath).isFile()) {
                    Toast.makeText (mContext,"请选择文件夹路径,不能选择文件!",Toast.LENGTH_SHORT).show ();
                    return;
                }
                mListener.onPathSelected(mCurrentPath);
            }
            dismiss();
        });

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                dpToPx(80),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(dpToPx(8), 0, 0, 0);
        buttonLayout.addView(btnCancel, buttonParams);
        buttonLayout.addView(btnConfirm, buttonParams);

        mainLayout.addView(buttonLayout);

        // 设置对话框内容
        LinearLayout.LayoutParams dialogParams = new LinearLayout.LayoutParams(
                dpToPx(320),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        setContentView(mainLayout, dialogParams);
    }

    private Button createButton(String text, int color) {
        Button button = new Button(mContext);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return button;
    }

    private android.graphics.drawable.Drawable createRoundedBackground() {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dpToPx(8));
        return drawable;
    }

    private void loadDirectory(String path) {
        // 显示加载状态
        showLoadingState();

        executor.execute(() -> {
            try {
                File directory = new File(path);
                if (!directory.exists() || !directory.isDirectory()) {
                    mainHandler.post(() -> {
                        Toast.makeText(mContext, "目录不存在或无法访问", Toast.LENGTH_SHORT).show();
                        showEmptyState();
                    });
                    return;
                }

                File[] files = directory.listFiles();
                List<File> fileList = new ArrayList<>();

                if (files != null) {
                    fileList.addAll(Arrays.asList(files));
                    // 排序：目录在前，文件在后，按名称排序
                    Collections.sort(fileList, new Comparator<File>() {
                        @Override
                        public int compare(File f1, File f2) {
                            if (f1.isDirectory() && !f2.isDirectory()) {
                                return -1;
                            } else if (!f1.isDirectory() && f2.isDirectory()) {
                                return 1;
                            } else {
                                return f1.getName().compareToIgnoreCase(f2.getName());
                            }
                        }
                    });
                }

                mainHandler.post(() -> {
                    updateCurrentPath(path);
                    displayFiles(fileList);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(mContext, "读取目录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showEmptyState();
                });
            }
        });
    }

    private void showLoadingState() {
        itemContainer.removeAllViews();
        TextView loadingText = new TextView(mContext);
        loadingText.setText("加载中...");
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, dpToPx(20), 0, dpToPx(20));
        loadingText.setTextColor(Color.GRAY);
        itemContainer.addView(loadingText);
    }

    private void showEmptyState() {
        itemContainer.removeAllViews();
        TextView emptyText = new TextView(mContext);
        emptyText.setText("目录为空或无法访问");
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, dpToPx(20), 0, dpToPx(20));
        emptyText.setTextColor(Color.GRAY);
        itemContainer.addView(emptyText);
    }

    private void updateCurrentPath(String path) {
        mCurrentPath = path + "/";
        tvCurrentPath.setText("当前路径: " + path);
    }

    private void displayFiles(List<File> files) {
        itemContainer.removeAllViews();
        selectedPath = null;
        selectedItemView = null;

        // 添加返回上级目录选项
        if (!mCurrentPath.equals("/")) {
            FileItemView parentItem = new FileItemView(mContext, "..", true, true);
            parentItem.setOnClickListener(v -> {
                File parent = new File(mCurrentPath).getParentFile();
                if (parent != null) {
                    loadDirectory(parent.getAbsolutePath());
                }
            });
            itemContainer.addView(parentItem);
        }

        // 添加文件和目录项
        for (File file : files) {
            FileItemView itemView = new FileItemView(mContext, file.getName(), file.isDirectory(), false);
            // 设置单击和双击监听器
            itemView.setOnItemClickListener((view, clickType) -> {
                if (clickType == FileItemView.ClickType.SINGLE_CLICK) {
                    onItemClick(itemView, file);
                } else if (clickType == FileItemView.ClickType.DOUBLE_CLICK) {
                    onItemDoubleClick(file);
                }
            });
            itemContainer.addView(itemView);
        }
    }

    // 优化：单击时立即高亮选中，不再延迟，提升体验
    private void onItemClick(FileItemView itemView, File file) {
        // 立即处理单击
        if (selectedItemView != null) {
            selectedItemView.setSelected(false);
        }
        selectedItemView = itemView;
        selectedItemView.setSelected(true);
        selectedPath = file.getAbsolutePath() + "/";
    }

    private void onItemDoubleClick(File file) {
        if (file.isDirectory()) {
            loadDirectory(file.getAbsolutePath());
        }
    }

    public void setOnPathSelectedListener(OnPathSelectedListener listener) {
        this.mListener = listener;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                mContext.getResources().getDisplayMetrics()
        );
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

// FileItemView.java - 文件项自定义View
class FileItemView extends LinearLayout {

    public enum ClickType {
        SINGLE_CLICK,
        DOUBLE_CLICK
    }

    public interface OnItemClickListener {
        void onItemClick(FileItemView view, ClickType clickType);
    }

    private TextView tvName;
    private TextView tvIcon;
    private boolean isDirectory;
    private boolean isParent;
    private OnItemClickListener itemClickListener;

    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_TIME_DELTA = 300; // 双击间隔时间
    private boolean doubleClickHandled = false;
    private Handler clickHandler = new Handler(Looper.getMainLooper());
    private Runnable singleClickRunnable;

    public FileItemView(Context context, String name, boolean isDirectory, boolean isParent) {
        super(context);
        this.isDirectory = isDirectory;
        this.isParent = isParent;
        initView();
        setFileName(name);
    }

    private void initView() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
        setFocusable(true);

        // 图标
        tvIcon = new TextView(getContext());
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvIcon.setGravity(Gravity.CENTER);
        LayoutParams iconParams = new LayoutParams(dpToPx(32), dpToPx(32));
        iconParams.setMargins(0, 0, dpToPx(12), 0);
        addView(tvIcon, iconParams);

        // 文件名
        tvName = new TextView(getContext());
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvName.setTextColor(Color.BLACK);
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams nameParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT);
        nameParams.weight = 1;
        addView(tvName, nameParams);

        // 设置点击效果
        setOnClickListener(v -> handleClick());
    }

    private void handleClick() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
            // 双击
            doubleClickHandled = true;
            if (singleClickRunnable != null) {
                clickHandler.removeCallbacks(singleClickRunnable);
            }
            if (itemClickListener != null) {
                itemClickListener.onItemClick(this, ClickType.DOUBLE_CLICK);
            }
        } else {
            // 立即处理单击（优化：不再延迟）
            doubleClickHandled = false;
            if (itemClickListener != null) {
                itemClickListener.onItemClick(this, ClickType.SINGLE_CLICK);
            }
        }

        lastClickTime = currentTime;
    }

    private void setFileName(String name) {
        tvName.setText(name);

        // 设置图标
        if (isParent) {
            tvIcon.setText("↩");
            tvIcon.setTextColor(0xFF2196F3);
        } else if (isDirectory) {
            tvIcon.setText("📁");
        } else {
            tvIcon.setText("📄");
        }
    }

    public void setSelected(boolean selected) {
        if (selected) {
            setBackgroundColor(0xFF2196F3);
            tvName.setTextColor(Color.WHITE);
        } else {
            setBackgroundColor(Color.TRANSPARENT);
            tvName.setTextColor(Color.BLACK);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public boolean isDoubleClicked() {
        return doubleClickHandled;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getContext().getResources().getDisplayMetrics()
        );
    }
}
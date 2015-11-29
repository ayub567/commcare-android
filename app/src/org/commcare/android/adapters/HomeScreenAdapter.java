package org.commcare.android.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.DisplayMetrics;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.commcare.android.ui.CustomBanner;
import org.commcare.android.view.ViewUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.activities.HomeButtons;

import java.util.List;
import java.util.Vector;

/**
 * Shows home screen buttons and header banner
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HomeScreenAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private final HomeCardDisplayData[] buttonData;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BUTTON = 1;
    private final int screenHeight, screenWidth;

    public HomeScreenAdapter(CommCareHomeActivity activity,
                             Vector<String> buttonsToHide,
                             boolean isDemoUser) {
        context = activity;
        buttonData = HomeButtons.buildButtonData(activity, buttonsToHide, isDemoUser);

        // get screen dimensions for drawing custom header image
        DisplayMetrics displaymetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        screenHeight = displaymetrics.heightPixels;
        screenWidth = displaymetrics.widthPixels;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_BUTTON) {
            View layoutView = inflater.inflate(R.layout.home_card, parent, false);
            return new SquareButtonViewHolder(layoutView);
        } else if (viewType == TYPE_HEADER) {
            View header = inflater.inflate(R.layout.grid_header_top_banner, parent, false);
            return new HeaderViewHolder(header);
        }

        throw new RuntimeException("no " + viewType +
                " type exists, should be TYPE_BUTTON or TYPE_HEADER");
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof SquareButtonViewHolder) {
            bindCard((SquareButtonViewHolder)holder, i, null);
        } else if (holder instanceof HeaderViewHolder) {
            bindHeader((HeaderViewHolder)holder);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder,
                                 int i, List<Object> payload) {
        if (holder instanceof SquareButtonViewHolder) {
            bindCard((SquareButtonViewHolder)holder, i, payload);
        } else if (holder instanceof HeaderViewHolder) {
            bindHeader((HeaderViewHolder)holder);
        }
    }

    private void bindCard(SquareButtonViewHolder squareButtonViewHolder,
                          int i, List<Object> payload) {
        HomeCardDisplayData cardDisplayData = getItem(i);
        String notificationText = null;

        if (payload != null) {
            notificationText = getLastPayloadString(payload);
        }

        cardDisplayData.textSetter.update(cardDisplayData,
                squareButtonViewHolder, context, notificationText);
        setupViewHolder(context, cardDisplayData, squareButtonViewHolder);
    }

    private HomeCardDisplayData getItem(int position) {
        return buttonData[position - 1];
    }

    private static String getLastPayloadString(List<Object> payload) {
        String lastPayloadString = null;
        for (Object entry : payload) {
            if (entry instanceof String) {
                lastPayloadString = (String)entry;
            }
        }
        return lastPayloadString;
    }

    private static void setupViewHolder(Context context,
                                        HomeCardDisplayData cardDisplayData,
                                        SquareButtonViewHolder squareButtonViewHolder) {
        final Drawable buttonDrawable =
                ContextCompat.getDrawable(context, cardDisplayData.imageResource);
        squareButtonViewHolder.imageView.setImageDrawable(buttonDrawable);
        squareButtonViewHolder.cardView.setOnClickListener(cardDisplayData.listener);

        StateListDrawable bgDrawable = getSLD(context, cardDisplayData.bgColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            squareButtonViewHolder.cardView.setBackground(bgDrawable);
        } else {
            squareButtonViewHolder.cardView.setBackgroundDrawable(bgDrawable);
        }
    }

    private static StateListDrawable getSLD(Context context,
                                            int backgroundColorRes) {
        final int backgroundColor =
                context.getResources().getColor(backgroundColorRes);
        ColorDrawable colorDrawable = new ColorDrawable(backgroundColor);
        ColorDrawable disabledColor =
                new ColorDrawable(context.getResources().getColor(R.color.grey));

        int color = ViewUtil.getColorDrawableColor(colorDrawable);

        float[] hsvOutput = new float[3];
        Color.colorToHSV(color, hsvOutput);

        hsvOutput[2] = (float)(hsvOutput[2] / 1.5);

        int selectedColor = Color.HSVToColor(hsvOutput);

        ColorDrawable pressedBackground = new ColorDrawable(selectedColor);

        StateListDrawable sld = new StateListDrawable();

        sld.addState(new int[]{-android.R.attr.state_enabled}, disabledColor);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
        sld.addState(StateSet.WILD_CARD, colorDrawable);
        return sld;
    }

    private void bindHeader(HeaderViewHolder headerHolder) {
        StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams)headerHolder.itemView.getLayoutParams();
        layoutParams.setFullSpan(true);

        boolean noCustomBanner =
                !CustomBanner.useCustomBanner(context, screenHeight,
                        screenWidth, headerHolder.headerImage);
        if (noCustomBanner) {
            headerHolder.headerImage.setImageResource(R.drawable.commcare_logo);
        }
    }

    @Override
    public int getItemCount() {
        // buttons and header
        return buttonData.length + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (isPositionHeader(position)) {
            return TYPE_HEADER;
        } else {
            return TYPE_BUTTON;
        }
    }

    private boolean isPositionHeader(int position) {
        return position == 0;
    }

    public int getSyncButtonPosition() {
        // NOTE PLM: assumes sync button is always the second to last button.
        return getItemCount() - 2;
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public final ImageView headerImage;

        public HeaderViewHolder(View itemView) {
            super(itemView);

            headerImage = (ImageView)itemView.findViewById(R.id.main_top_banner);
        }
    }
}

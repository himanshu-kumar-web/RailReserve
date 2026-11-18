package com.railreserve.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ScrollView;

/** Lets large headers/filters scroll without squeezing a populated list out of a short window. */
public final class BoundedHeaderScrollView extends ScrollView {
    public BoundedHeaderScrollView(Context context,AttributeSet attrs) { super(context,attrs); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            boolean hasRows=false;
            int reservedRows=0;
            if (getParent() instanceof ViewGroup) {
                ViewGroup parent=(ViewGroup)getParent();
                for(int i=0;i<parent.getChildCount();i++) {
                    View sibling=parent.getChildAt(i);
                    if(sibling instanceof ListView) {
                        ListView list=(ListView)sibling;
                        if (list.getAdapter()!=null && list.getAdapter().getCount()>0) {
                            hasRows=true;
                            reservedRows+=Math.round(80*getResources().getDisplayMetrics().density);
                            if (list.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                                ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)list.getLayoutParams();
                                reservedRows+=margins.topMargin+margins.bottomMargin;
                            }
                        }
                    }
                }
            }
            // Empty/error states get more space; populated lists retain a useful scrolling area.
            int maximum=Math.round(MeasureSpec.getSize(heightSpec)*(hasRows ? 0.55f : 0.90f));
            if (hasRows) maximum=Math.min(maximum,Math.max(0,MeasureSpec.getSize(heightSpec)-reservedRows));
            heightSpec=MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}

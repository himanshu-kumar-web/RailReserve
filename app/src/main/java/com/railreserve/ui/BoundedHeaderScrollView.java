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
            if (getParent() instanceof ViewGroup) {
                ViewGroup parent=(ViewGroup)getParent();
                for(int i=0;i<parent.getChildCount();i++) {
                    View sibling=parent.getChildAt(i);
                    if(sibling instanceof ListView) {
                        ListView list=(ListView)sibling;
                        hasRows=list.getAdapter()!=null && list.getAdapter().getCount()>0;
                    }
                }
            }
            // Empty/error states get more space; populated lists retain a useful scrolling area.
            int maximum=Math.round(MeasureSpec.getSize(heightSpec)*(hasRows ? 0.55f : 0.90f));
            heightSpec=MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}

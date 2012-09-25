package Dont.Irradiate.Me.Bro.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import Dont.Irradiate.Me.Bro.R;

/**
 * Created by Orbotix Inc.
 * Date: 4/18/12
 *
 * @author Adam Williams
 */
public class FooterView extends RelativeLayout {

    public FooterView(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.footer_view, this);
    }
}

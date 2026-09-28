package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Role/Agent Settings: live status + stable aliases. Session history intentionally stays out. */
final class RoleSettingsDialog {
    private RoleSettingsDialog() {}

    static void show(Activity activity, CalendarProcess process, Runnable onChanged) {
        if (activity == null || process == null || activity.isFinishing()) return;
        RoleProfileStore.Profile profile = RoleProfileStore.resolve(activity, process.role);
        if (profile == null) return;
        RoleProfileStore.EditSession edit = RoleProfileStore.beginEdit(activity, profile.id);
        if (edit == null) return;
        new Controller(activity, edit,
                new Status(true, process.project, process.topic,
                        process.workStartMillis(), process.beginMillis, 0L),
                onChanged).show();
    }

    static void show(Activity activity, IdleProcessState.IdleRole idle, Runnable onChanged) {
        if (activity == null || idle == null || activity.isFinishing()) return;
        RoleProfileStore.Profile profile = RoleProfileStore.findById(activity, idle.key);
        if (profile == null) profile = RoleProfileStore.resolve(activity, idle.role);
        if (profile == null) return;
        RoleProfileStore.EditSession edit = RoleProfileStore.beginEdit(activity, profile.id);
        if (edit == null) return;
        new Controller(activity, edit,
                new Status(false, idle.project, idle.topic,
                        idle.lastStartedMillis, 0L, idle.lastFinishedMillis),
                onChanged).show();
    }

    private static final class Status {
        final boolean active;
        final String project;
        final String topic;
        final long startedMillis;
        final long deadlineMillis;
        final long finishedMillis;
        Status(boolean active, String project, String topic, long startedMillis,
                long deadlineMillis, long finishedMillis) {
            this.active=active; this.project=clean(project); this.topic=clean(topic);
            this.startedMillis=Math.max(0L,startedMillis);
            this.deadlineMillis=Math.max(0L,deadlineMillis);
            this.finishedMillis=Math.max(0L,finishedMillis);
        }
    }

    private static final class Controller {
        final Activity activity;
        final RoleProfileStore.EditSession edit;
        final Status status;
        final Runnable onChanged;
        final boolean dark;
        final ScrollView scroll;
        final LinearLayout content;
        AlertDialog dialog;

        Controller(Activity activity, RoleProfileStore.EditSession edit,
                Status status, Runnable onChanged) {
            this.activity=activity; this.edit=edit; this.status=status; this.onChanged=onChanged;
            this.dark=Ui.isDark(activity);
            this.scroll=new ScrollView(activity);
            this.content=new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(Ui.dp(activity,20),Ui.dp(activity,8),
                    Ui.dp(activity,20),Ui.dp(activity,16));
            scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        }

        void show() {
            render(false);
            dialog=new AlertDialog.Builder(activity).setTitle("Role settings").setView(scroll)
                    .setNegativeButton("Cancel",null).setPositiveButton("Done",null).create();
            dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> commitAndClose()));
            dialog.show();
        }

        void commitAndClose() {
            String error=edit.commit(activity);
            if(!error.isEmpty()){ Toast.makeText(activity,error,Toast.LENGTH_LONG).show(); return; }
            dialog.dismiss();
            if(onChanged!=null) onChanged.run();
        }

        void render(boolean preserveScroll) {
            int scrollY=preserveScroll?scroll.getScrollY():0;
            RoleProfileStore.Profile profile=edit.profile();
            content.removeAllViews();
            addStatus(profile);
            addAliases(profile);
            if(preserveScroll) scroll.post(() -> scroll.scrollTo(0,scrollY));
        }

        void addStatus(RoleProfileStore.Profile profile) {
            content.addView(sectionTitle("Current status"));
            LinearLayout card=Ui.card(activity,dark);
            card.setPadding(Ui.dp(activity,16),Ui.dp(activity,14),
                    Ui.dp(activity,16),Ui.dp(activity,14));
            TextView title=Ui.text(activity,profile.primaryAlias,17f,Ui.mainText(dark));
            title.setTypeface(Ui.mediumTypeface(activity)); card.addView(title);
            long now=System.currentTimeMillis();
            String stateText=status.active
                    ? "Active · "+formatRemaining(status.deadlineMillis-now)+" remaining"
                    : "Idle · "+formatDuration(now-status.finishedMillis);
            TextView state=Ui.text(activity,stateText,12.5f,
                    status.active?Ui.accent(activity,dark):Ui.secondaryText(dark));
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
            sp.setMargins(0,Ui.dp(activity,4),0,0); card.addView(state,sp);
            if(!status.project.isEmpty()) addMeta(card,status.project,8);
            if(!status.topic.isEmpty()) addMeta(card,status.topic,4);
            if(!status.active && status.startedMillis>0L && status.finishedMillis>status.startedMillis) {
                addMeta(card,"Last session · "+formatDuration(status.finishedMillis-status.startedMillis),5);
            }
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(0,Ui.dp(activity,7),0,Ui.dp(activity,18)); content.addView(card,cp);
        }

        void addMeta(LinearLayout card,String text,int topDp){
            TextView v=Ui.text(activity,text,13f,Ui.secondaryText(dark));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(0,Ui.dp(activity,topDp),0,0); card.addView(v,p);
        }

        void addAliases(RoleProfileStore.Profile profile) {
            content.addView(sectionTitle("Known aliases"));
            for(String alias:profile.aliases){
                boolean primary=ProjectProfileRules.normalizeAlias(alias).equals(
                        ProjectProfileRules.normalizeAlias(profile.primaryAlias));
                boolean calendar=edit.isCalendarAlias(alias);
                LinearLayout row=new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView provenance=miniIcon(calendar?R.drawable.ic_alias_calendar:R.drawable.ic_project_pencil,
                        calendar?"Calendar alias: copy to edit":"Manual alias: edit");
                provenance.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
                provenance.setOnClickListener(v -> editAlias(alias)); row.addView(provenance,
                        new LinearLayout.LayoutParams(Ui.dp(activity,30),Ui.dp(activity,30)));
                TextView aliasText=Ui.text(activity,alias+(primary?" · Primary":""),13.5f,Ui.mainText(dark));
                aliasText.setClickable(true); aliasText.setOnClickListener(v -> editAlias(alias));
                LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1f);
                ap.setMargins(Ui.dp(activity,5),0,0,0); row.addView(aliasText,ap);
                if(!primary){
                    Button make=miniButton("Make Primary");
                    make.setOnClickListener(v -> { String e=edit.makePrimary(alias); if(showError(e)) return; changed(); });
                    row.addView(make,new LinearLayout.LayoutParams(-2,Ui.dp(activity,38)));
                    ImageView del=miniIcon(R.drawable.ic_idle_trash,"Delete alias");
                    del.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
                    del.setOnClickListener(v -> { String e=edit.deleteAlias(alias); if(showError(e)) return; changed(); });
                    LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(Ui.dp(activity,32),Ui.dp(activity,32));
                    dp.setMargins(Ui.dp(activity,4),0,0,0); row.addView(del,dp);
                }
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
                rp.setMargins(0,Ui.dp(activity,6),0,0); content.addView(row,rp);
            }
            Button add=Ui.button(activity,"Add alias",false,dark); add.setTextSize(14);
            add.setOnClickListener(v -> addAlias());
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(activity,48));
            p.setMargins(0,Ui.dp(activity,10),0,0); content.addView(add,p);
        }

        boolean showError(String error){
            if(error==null||error.isEmpty()) return false;
            Toast.makeText(activity,error,Toast.LENGTH_LONG).show(); return true;
        }

        void editAlias(String alias) {
            EditText input=new EditText(activity); input.setSingleLine(true); input.setText(alias);
            input.setSelection(input.length());
            AlertDialog child=new AlertDialog.Builder(activity).setTitle("Edit role alias")
                    .setView(inputFrame(input)).setNegativeButton("Cancel",null)
                    .setPositiveButton("OK",null).create();
            child.setOnShowListener(x -> child.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String e=edit.editAlias(alias,input.getText()==null?"":input.getText().toString());
                if(!e.isEmpty()){input.setError(e);return;} child.dismiss(); changed();
            })); child.show();
        }

        void addAlias() {
            EditText input=new EditText(activity); input.setSingleLine(true); input.setHint("Role alias");
            AlertDialog child=new AlertDialog.Builder(activity).setTitle("Add role alias")
                    .setView(inputFrame(input)).setNegativeButton("Cancel",null)
                    .setPositiveButton("Add",null).create();
            child.setOnShowListener(x -> child.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String e=edit.addAlias(input.getText()==null?"":input.getText().toString());
                if(!e.isEmpty()){input.setError(e);return;} child.dismiss(); changed();
            })); child.show();
        }

        FrameLayout inputFrame(EditText input){
            FrameLayout frame=new FrameLayout(activity); int pad=Ui.dp(activity,20);
            frame.setPadding(pad,0,pad,0);
            frame.addView(input,new FrameLayout.LayoutParams(-1,Ui.dp(activity,54))); return frame;
        }
        void changed(){render(true);}
        TextView sectionTitle(String text){
            TextView t=Ui.text(activity,text,13f,Ui.secondaryText(dark));
            t.setTypeface(Ui.mediumTypeface(activity)); return t;
        }
        ImageView miniIcon(int drawable,String description){
            ImageView i=new ImageView(activity); i.setImageResource(drawable); i.setContentDescription(description);
            i.setClickable(true); i.setFocusable(true); i.setPadding(Ui.dp(activity,6),Ui.dp(activity,6),
                    Ui.dp(activity,6),Ui.dp(activity,6)); return i;
        }
        Button miniButton(String text){
            Button b=new Button(activity); b.setAllCaps(false); b.setText(text); b.setTextSize(11);
            b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
            b.setPadding(Ui.dp(activity,8),0,Ui.dp(activity,8),0); return b;
        }
    }

    private static String formatRemaining(long millis){
        long m=Math.max(0L,(Math.max(0L,millis)+59_999L)/60_000L);
        if(m<60L)return m+"m"; long h=m/60L,r=m%60L; return r==0L?h+"h":h+"h "+r+"m";
    }
    private static String formatDuration(long millis){
        long m=Math.max(0L,Math.max(0L,millis)/60_000L);
        if(m<60L)return m+"m"; long h=m/60L,r=m%60L; return r==0L?h+"h":h+"h "+r+"m";
    }
    private static String clean(String v){return v==null?"":v.trim();}
}

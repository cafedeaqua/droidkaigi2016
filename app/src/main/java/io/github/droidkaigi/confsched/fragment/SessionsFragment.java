package io.github.droidkaigi.confsched.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;

import io.github.droidkaigi.confsched.MainApplication;
import io.github.droidkaigi.confsched.R;
import io.github.droidkaigi.confsched.activity.ActivityNavigator;
import io.github.droidkaigi.confsched.api.DroidKaigiClient;
import io.github.droidkaigi.confsched.dao.SessionDao;
import io.github.droidkaigi.confsched.databinding.FragmentSessionsBinding;
import io.github.droidkaigi.confsched.model.MainContentStateBrokerProvider;
import io.github.droidkaigi.confsched.model.Page;
import io.github.droidkaigi.confsched.model.Session;
import io.github.droidkaigi.confsched.util.AppUtil;
import io.github.droidkaigi.confsched.util.DateUtil;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class SessionsFragment extends Fragment {

    public static final String TAG = SessionsFragment.class.getSimpleName();

    @Inject
    DroidKaigiClient client;
    @Inject
    SessionDao dao;
    @Inject
    CompositeSubscription compositeSubscription;
    @Inject
    ActivityNavigator activityNavigator;
    @Inject
    MainContentStateBrokerProvider brokerProvider;

    private SessionsPagerAdapter adapter;
    private FragmentSessionsBinding binding;

    public static SessionsFragment newInstance() {
        return new SessionsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSessionsBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        initViewPager();
        initEmptyView();
        compositeSubscription.add(loadData());
        compositeSubscription.add(fetchAndSave());
        return binding.getRoot();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        MainApplication.getComponent(this).inject(this);
    }

    private void initViewPager() {
        adapter = new SessionsPagerAdapter(getFragmentManager());
        binding.viewPager.setAdapter(adapter);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
    }

    private void initEmptyView() {
        binding.emptyViewButton.setOnClickListener(v -> {
            brokerProvider.get().set(Page.ALL_SESSIONS);
        });
    }

    private Subscription fetchAndSave() {
        return client.getSessions(AppUtil.getCurrentLanguageId(getActivity()))
                .doOnNext(dao::updateAll)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    protected Subscription loadData() {
        Observable<List<Session>> cachedSessions = dao.findAll();
        return cachedSessions.flatMap(sessions -> {
            if (sessions.isEmpty()) {
                return client.getSessions(AppUtil.getCurrentLanguageId(getActivity())).doOnNext(dao::updateAll);
            } else {
                return Observable.just(sessions);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::groupByDateSessions,
                        throwable -> Log.e(TAG, "Load failed", throwable)
                );
    }

    protected void showEmptyView() {
        binding.emptyView.setVisibility(View.VISIBLE);
    }

    protected void hideEmptyView() {
        binding.emptyView.setVisibility(View.GONE);
    }

    protected void groupByDateSessions(List<Session> sessions) {
        Map<String, List<Session>> sessionsByDate = new TreeMap<>();
        for (Session session : sessions) {
            String key = DateUtil.getMonthDate(session.stime, getActivity());
            if (sessionsByDate.containsKey(key)) {
                sessionsByDate.get(key).add(session);
            } else {
                List<Session> list = new ArrayList<>();
                list.add(session);
                sessionsByDate.put(key, list);
            }
        }

        for (Map.Entry<String, List<Session>> e : sessionsByDate.entrySet()) {
            addFragment(e.getKey(), e.getValue());
        }

        binding.tabLayout.setupWithViewPager(binding.viewPager);

        if (sessions.isEmpty()) {
            showEmptyView();
        } else {
            hideEmptyView();
        }
    }

    private void addFragment(String title, List<Session> sessions) {
        SessionsTabFragment fragment = SessionsTabFragment.newInstance(sessions);
        adapter.add(title, fragment);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_sessions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_search:
                activityNavigator.showSearch(getActivity());
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = adapter.getItem(binding.viewPager.getCurrentItem());
        if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data);
    }

    private class SessionsPagerAdapter extends FragmentStatePagerAdapter {

        private List<SessionsTabFragment> fragments = new ArrayList<>();
        private List<String> titles = new ArrayList<>();

        public SessionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles.get(position);
        }

        public void add(String title, SessionsTabFragment fragment) {
            fragments.add(fragment);
            titles.add(title);
            notifyDataSetChanged();
        }

    }

}

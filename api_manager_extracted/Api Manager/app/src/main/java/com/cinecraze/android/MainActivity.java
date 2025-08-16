package com.cinecraze.android;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.cinecraze.android.fragments.TMDBGeneratorFragment;
import com.cinecraze.android.fragments.ManualInputFragment;
import com.cinecraze.android.fragments.BulkOperationsFragment;
import com.cinecraze.android.fragments.DataManagementFragment;
import com.cinecraze.android.fragments.AutoEmbedFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.content.Intent;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        fabPreview = findViewById(R.id.fab_preview);

        // Setup ViewPager2 with fragments
        setupViewPager();

        // Setup TabLayout with ViewPager2
        setupTabLayout();

        // Setup bottom navigation
        setupBottomNavigation();

        // Setup preview FAB
        if (fabPreview != null) {
            fabPreview.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
                startActivity(intent);
            });
        }

        // Set default fragment
        if (savedInstanceState == null) {
            viewPager.setCurrentItem(0);
        }
    }

    private void setupViewPager() {
        FragmentStateAdapter pagerAdapter = new FragmentStateAdapter(this) {
            @Override
            public int getItemCount() {
                return 5; // TMDB, Manual, Bulk, Data, Auto-Embed
            }

            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new TMDBGeneratorFragment();
                    case 1:
                        return new ManualInputFragment();
                    case 2:
                        return new BulkOperationsFragment();
                    case 3:
                        return new DataManagementFragment();
                    case 4:
                        return new AutoEmbedFragment();
                    default:
                        return new TMDBGeneratorFragment();
                }
            }
        };

        viewPager.setAdapter(pagerAdapter);
        viewPager.setUserInputEnabled(false); // Disable swipe
    }

    private void setupTabLayout() {
        String[] tabTitles = {
            "TMDB Generator",
            "Manual Input", 
            "Bulk Operations",
            "Data Management",
            "Auto-Embed"
        };

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(tabTitles[position]);
        }).attach();
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            int position = 0;

            if (itemId == R.id.nav_tmdb) {
                position = 0;
            } else if (itemId == R.id.nav_manual) {
                position = 1;
            } else if (itemId == R.id.nav_bulk) {
                position = 2;
            } else if (itemId == R.id.nav_data) {
                position = 3;
            } else if (itemId == R.id.nav_servers) {
                position = 4;
            }

            viewPager.setCurrentItem(position);
            tabLayout.selectTab(tabLayout.getTabAt(position));
            return true;
        });

        // Sync ViewPager with bottom navigation
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNavigationView.setSelectedItemId(R.id.nav_tmdb);
                        break;
                    case 1:
                        bottomNavigationView.setSelectedItemId(R.id.nav_manual);
                        break;
                    case 2:
                        bottomNavigationView.setSelectedItemId(R.id.nav_bulk);
                        break;
                    case 3:
                        bottomNavigationView.setSelectedItemId(R.id.nav_data);
                        break;
                    case 4:
                        bottomNavigationView.setSelectedItemId(R.id.nav_servers);
                        break;
                }
            }
        });
    }


}
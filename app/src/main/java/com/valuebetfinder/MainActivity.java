package com.valuebetfinder;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final BettingRepository repository = new BettingRepository();
    private final ArbitrageSyncScheduler arbitrageSyncScheduler =
            new ArbitrageSyncScheduler(new ArbitrageApiClient());
    private final AiPicksSyncScheduler aiPicksSyncScheduler =
            new AiPicksSyncScheduler(new AiPicksApiClient());

    private LocalTicketStore localTicketStore;
    private LinearLayout content;
    private TextView syncStatus;
    private final Handler dayHandler = new Handler(Looper.getMainLooper());
    private final Calendar selectedAiCalendar = Calendar.getInstance();
    private Runnable midnightRollover;
    private List<AiPick> aiPicks;
    private List<ValueBet> valueBets;
    private List<ArbitrageOpportunity> arbitrageOpportunities;
    private String screen = "ai";
    private String aiHistoryMode = "parlay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        localTicketStore = new LocalTicketStore(this);
        localTicketStore.rolloverAiLatestIfOld();
        aiPicks = localTicketStore.loadAiLatest();
        if (aiPicks.isEmpty()) {
            aiPicks = repository.getAiDailyPicks();
        }
        valueBets = repository.getTopValueBets();
        arbitrageOpportunities = localTicketStore.loadArbLatest();
        if (arbitrageOpportunities.isEmpty()) {
            arbitrageOpportunities = repository.getArbitrageOpportunities();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.screen_bg));
        root.addView(appHeader(), blockParams());

        ScrollView scrollView = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(18));
        scrollView.addView(content);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        root.addView(bottomNavigation());

        setContentView(root);
        render();
        startSync();
        scheduleMidnightRollover();
    }

    private LinearLayout appHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(10));

        ImageView menu = new ImageView(this);
        menu.setImageResource(R.drawable.header_menu);
        menu.setContentDescription("Meni");
        menu.setPadding(dp(3), dp(3), dp(3), dp(3));
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Meni ce biti dodat u sledecoj verziji.", Toast.LENGTH_SHORT).show();
            }
        });
        header.addView(menu, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.arbiedge_mark);
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(30), dp(30)));

        SpannableString brandText = new SpannableString("ArbiEdge");
        brandText.setSpan(
                new ForegroundColorSpan(color(R.color.accent)),
                4,
                brandText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        TextView brandLabel = text(brandText.toString(), 20, R.color.ink, Typeface.BOLD);
        brandLabel.setText(brandText);
        brandLabel.setPadding(dp(6), 0, 0, 0);
        brand.addView(brandLabel, blockParams());
        header.addView(brand, weightedParams());

        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.header_bell);
        bell.setContentDescription("Notifikacije");
        bell.setPadding(dp(3), dp(3), dp(3), dp(3));
        bell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Notifikacije nisu jos ukljucene.", Toast.LENGTH_SHORT).show();
            }
        });
        header.addView(bell, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return header;
    }

    @Override
    protected void onDestroy() {
        arbitrageSyncScheduler.stop();
        aiPicksSyncScheduler.stop();
        if (midnightRollover != null) {
            dayHandler.removeCallbacks(midnightRollover);
        }
        super.onDestroy();
    }

    private void render() {
        content.removeAllViews();
        if ("arb".equals(screen)) {
            addArbScreen(content);
        } else if ("pyramid".equals(screen)) {
            addPyramidScreen(content);
        } else {
            addAiScreen(content);
        }
    }

    private void startSync() {
        aiPicksSyncScheduler.start(new AiPicksSyncCallback() {
            @Override
            public void onSuccess(List<AiPick> picks) {
                aiPicks = picks;
                localTicketStore.saveAiLatest(picks);
                render();
                setSyncStatus("AI picks azurirani");
            }

            @Override
            public void onError(Exception exception) {
                setSyncStatus("AI backend nije dostupan - prikazan je lokalni zapis");
            }
        });

        arbitrageSyncScheduler.start(new ArbitrageSyncCallback() {
            @Override
            public void onSuccess(List<ArbitrageOpportunity> opportunities) {
                arbitrageOpportunities = opportunities;
                localTicketStore.saveArbLatest(opportunities);
                render();
                setSyncStatus("ARB azuriran");
            }

            @Override
            public void onError(Exception exception) {
                setSyncStatus("ARB backend nije dostupan - prikazan je lokalni zapis");
            }
        });
    }

    private void addAiScreen(LinearLayout parent) {
        addScreenTitle(parent, "AI PICKS", R.color.accent);
        addSyncStatus(parent);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView dateButton = actionButton(selectedAiDate(), R.color.blue, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAiDatePicker();
            }
        });
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f);
        dateParams.rightMargin = dp(10);
        controls.addView(dateButton, dateParams);
        controls.addView(actionButton("OSVEZI PICKOVE", R.color.accent, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSyncStatus("Ucitavam pickove sa PC-a...");
                aiPicksSyncScheduler.refresh(new AiPicksSyncCallback() {
                    @Override
                    public void onSuccess(List<AiPick> picks) {
                        aiPicks = picks;
                        localTicketStore.saveAiLatest(picks);
                        render();
                        setSyncStatus("AI picks osvezeni sa PC-a");
                    }

                    @Override
                    public void onError(Exception exception) {
                        setSyncStatus("Ne mogu da ucitam sa PC-a: " + shortError(exception));
                    }
                });
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.18f));
        parent.addView(controls, topBlockParams(8));

        AiPick mvp = findMvpPick();
        if (mvp != null) {
            parent.addView(aiPickCard("MVP PICK", mvp, true), topBlockParams(18));
        }

        int index = 1;
        for (AiPick pick : aiPicks) {
            parent.addView(aiPickCard("PICK " + index, pick, false), topBlockParams(16));
            index++;
            if (index > 3) {
                break;
            }
        }

        if (aiPicks.isEmpty()) {
            parent.addView(emptyCard("Nema AI pickova.", "Na PC sajtu stisni NADJI AI PICKS, pa ovde OSVEZI PICKOVE."), topBlockParams(20));
        } else {
            parent.addView(summaryCard("PARLAY", parlaySummary(aiPicks)), topBlockParams(16));
            addAiResultButtons(parent);
        }

        addAiHistory(parent);
    }

    private void addPyramidScreen(LinearLayout parent) {
        addScreenTitle(parent, "MVP PYRAMID", R.color.orange);
        addSyncStatus(parent);
        addMvpPyramid(parent);
    }

    private void addMvpPyramid(LinearLayout parent) {
        parent.addView(sectionLabel("MVP PIRAMIDA", R.color.orange), topBlockParams(18));
        AiPick mvp = findMvpPick();
        MvpPyramidState state = localTicketStore.loadMvpPyramid();

        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        String status = state.isActive()
                ? "Aktivna piramida - nivo " + state.getLevel()
                : "Piramida nije pokrenuta";
        card.addView(text(status, 16, R.color.ink, Typeface.BOLD));

        TextView details = text(mvpPyramidSummary(state, mvp), 13, R.color.muted, Typeface.NORMAL);
        details.setPadding(0, dp(8), 0, 0);
        card.addView(details);

        if (!state.isActive()) {
            final EditText stakeInput = new EditText(this);
            stakeInput.setSingleLine(true);
            stakeInput.setText("1000");
            stakeInput.setTextSize(16);
            stakeInput.setTextColor(color(R.color.ink));
            stakeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            stakeInput.setSelectAllOnFocus(true);
            stakeInput.setPadding(dp(12), dp(8), dp(12), dp(8));
            stakeInput.setHint("Pocetni ulog");
            LinearLayout.LayoutParams inputParams = blockParams();
            inputParams.topMargin = dp(12);
            card.addView(stakeInput, inputParams);

            TextView start = actionButton("START PIRAMIDA", R.color.orange, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startMvpPyramid(parseMoney(stakeInput.getText().toString(), 1000.0));
                }
            });
            card.addView(start, topBlockParams(12));
        } else {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(actionButton("DOSAO JE", R.color.accent, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    markMvpPyramidWin();
                }
            }), weightedParams());
            LinearLayout.LayoutParams midParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            midParams.leftMargin = dp(8);
            row.addView(actionButton("PAO JE", R.color.blue, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    markMvpPyramidLoss();
                }
            }), midParams);
            card.addView(row, topBlockParams(12));

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setGravity(Gravity.CENTER_VERTICAL);
            row2.addView(actionButton("CASH OUT", R.color.orange, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cashOutMvpPyramid();
                }
            }), weightedParams());
            LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            resetParams.leftMargin = dp(8);
            row2.addView(actionButton("RESET", R.color.blue, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    localTicketStore.resetMvpPyramid();
                    render();
                    setSyncStatus("MVP piramida resetovana");
                }
            }), resetParams);
            card.addView(row2, topBlockParams(10));
        }

        parent.addView(card, topBlockParams(10));
    }

    private void addAiHistory(LinearLayout parent) {
        parent.addView(sectionLabel("ISTORIJA TIKETA", R.color.accent), topBlockParams(28));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(actionButton("PARLAY HISTORY", "parlay".equals(aiHistoryMode) ? R.color.accent : R.color.blue, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                aiHistoryMode = "parlay";
                render();
            }
        }), weightedParams());
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightParams.leftMargin = dp(8);
        row.addView(actionButton("MVP HISTORY", "mvp".equals(aiHistoryMode) ? R.color.accent : R.color.blue, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                aiHistoryMode = "mvp";
                render();
            }
        }), rightParams);
        parent.addView(row, topBlockParams(10));

        List<AiTicketRecord> history = "mvp".equals(aiHistoryMode)
                ? localTicketStore.loadAiMvpHistory()
                : localTicketStore.loadAiParlayHistory();
        if (history.isEmpty()) {
            parent.addView(emptyCard("Nema istorije", "Kad tiket prodje ili padne, stisni odgovarajuce dugme i upisace se ovde."), topBlockParams(10));
            return;
        }
        int shown = 0;
        for (AiTicketRecord record : history) {
            if (record.getPicks().isEmpty()) {
                continue;
            }
            parent.addView(summaryCard(ticketHistoryTitle(record), ticketHistoryBody(record)), topBlockParams(10));
            shown++;
            if (shown == 8) {
                break;
            }
        }
    }

    private void addAiResultButtons(LinearLayout parent) {
        LinearLayout parlayRow = new LinearLayout(this);
        parlayRow.setOrientation(LinearLayout.HORIZONTAL);
        parlayRow.addView(actionButton("PARLAY PROSAO", R.color.accent, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localTicketStore.saveAiParlayResult(firstThreePicks(), "WIN");
                aiHistoryMode = "parlay";
                render();
                setSyncStatus("Parlay upisan kao PROSAO");
            }
        }), weightedParams());
        LinearLayout.LayoutParams parlayLossParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        parlayLossParams.leftMargin = dp(8);
        parlayRow.addView(actionButton("PARLAY PAO", R.color.blue, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localTicketStore.saveAiParlayResult(firstThreePicks(), "LOSS");
                aiHistoryMode = "parlay";
                render();
                setSyncStatus("Parlay upisan kao PAO");
            }
        }), parlayLossParams);
        parent.addView(parlayRow, topBlockParams(12));

        LinearLayout mvpRow = new LinearLayout(this);
        mvpRow.setOrientation(LinearLayout.HORIZONTAL);
        mvpRow.addView(actionButton("MVP PROSAO", R.color.accent, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localTicketStore.saveAiMvpResult(findMvpPick(), "WIN");
                aiHistoryMode = "mvp";
                render();
                setSyncStatus("MVP upisan kao PROSAO");
            }
        }), weightedParams());
        LinearLayout.LayoutParams mvpLossParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mvpLossParams.leftMargin = dp(8);
        mvpRow.addView(actionButton("MVP PAO", R.color.blue, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                localTicketStore.saveAiMvpResult(findMvpPick(), "LOSS");
                aiHistoryMode = "mvp";
                render();
                setSyncStatus("MVP upisan kao PAO");
            }
        }), mvpLossParams);
        parent.addView(mvpRow, topBlockParams(8));
    }

    private LinearLayout aiPickCard(String label, final AiPick pick, boolean highlight) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (highlight) {
            card.setBackgroundColor(color(R.color.soft_green));
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(label, 13, R.color.accent, Typeface.BOLD), weightedParams());

        TextView odds = text(pick.getOddsLabel(), 14, android.R.color.white, Typeface.BOLD);
        odds.setGravity(Gravity.CENTER);
        odds.setBackgroundResource(R.drawable.green_button_bg);
        top.addView(odds, new LinearLayout.LayoutParams(dp(78), dp(38)));
        card.addView(top);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(0, dp(18), 0, 0);

        TextView ball = text("o", 28, R.color.ink, Typeface.BOLD);
        ball.setGravity(Gravity.CENTER);
        body.addView(ball, new LinearLayout.LayoutParams(dp(46), dp(58)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(pick.getMatch(), 18, R.color.ink, Typeface.BOLD));
        copy.addView(text(pick.getSelection(), 14, R.color.ink, Typeface.BOLD));
        copy.addView(text(metaLine(pick), 13, R.color.muted, Typeface.NORMAL));
        if (!pick.getBookmaker().isEmpty()) {
            copy.addView(text(pick.getBookmaker(), 12, R.color.muted, Typeface.NORMAL));
        }
        body.addView(copy, weightedParams());

        TextView star = text("*", 30, R.color.accent, Typeface.NORMAL);
        star.setGravity(Gravity.CENTER);
        body.addView(star, new LinearLayout.LayoutParams(dp(34), dp(58)));

        card.addView(body);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAiPickDetail(pick);
            }
        });
        return card;
    }

    private String mvpPyramidSummary(MvpPyramidState state, AiPick mvp) {
        StringBuilder builder = new StringBuilder();
        if (mvp == null) {
            builder.append("Prvo generisi AI picks da bi piramida imala MVP pick.");
            if (state.getBankedProfit() != 0.0) {
                builder.append("\nZakljucan profit: ").append(state.bankedProfitLabel());
            }
            return builder.toString();
        }

        if (state.isActive()) {
            double nextPayout = state.getCurrentStake() * Math.max(1.0, mvp.getOdds());
            builder.append("Sledeci MVP: ")
                    .append(mvp.getMatch())
                    .append(" - ")
                    .append(mvp.getSelection())
                    .append(" @ ")
                    .append(mvp.getOddsLabel());
            builder.append("\nSledeci ulog: ")
                    .append(state.currentStakeLabel())
                    .append(" | Ako dodje, imas: ")
                    .append(String.format(Locale.US, "%.2f", nextPayout));
            builder.append("\nPocetni ulog: ")
                    .append(String.format(Locale.US, "%.2f", state.getStartStake()))
                    .append(" | Zakljucan profit: ")
                    .append(state.bankedProfitLabel());
        } else {
            builder.append("Danasnji MVP: ")
                    .append(mvp.getMatch())
                    .append(" - ")
                    .append(mvp.getSelection())
                    .append(" @ ")
                    .append(mvp.getOddsLabel());
            builder.append("\nStartuj piramidu pocetnim ulogom. Ako MVP dodje, dobitak postaje sledeci ulog.");
            if (state.getBankedProfit() != 0.0) {
                builder.append("\nZakljucan profit: ").append(state.bankedProfitLabel());
            }
        }
        return builder.toString();
    }

    private void startMvpPyramid(double startStake) {
        AiPick mvp = findMvpPick();
        if (mvp == null || mvp.getOdds() <= 1.0 || startStake <= 0.0) {
            setSyncStatus("Piramida ne moze da krene bez MVP picka i validnog uloga");
            return;
        }
        localTicketStore.saveMvpPyramid(new MvpPyramidState(
                true,
                1,
                startStake,
                startStake,
                localTicketStore.loadMvpPyramid().getBankedProfit(),
                mvp.getMatch(),
                mvp.getSelection()
        ));
        render();
        setSyncStatus("MVP piramida pokrenuta");
    }

    private void markMvpPyramidWin() {
        MvpPyramidState state = localTicketStore.loadMvpPyramid();
        AiPick mvp = findMvpPick();
        if (!state.isActive() || mvp == null || mvp.getOdds() <= 1.0) {
            setSyncStatus("Nema aktivne piramide ili MVP kvote");
            return;
        }
        double payout = state.getCurrentStake() * mvp.getOdds();
        localTicketStore.saveMvpPyramid(new MvpPyramidState(
                true,
                state.getLevel() + 1,
                state.getStartStake(),
                payout,
                state.getBankedProfit(),
                mvp.getMatch(),
                mvp.getSelection()
        ));
        render();
        setSyncStatus(String.format(Locale.US, "MVP dosao - sada imas %.2f za sledeci MVP", payout));
    }

    private void markMvpPyramidLoss() {
        MvpPyramidState state = localTicketStore.loadMvpPyramid();
        if (!state.isActive()) {
            setSyncStatus("Nema aktivne MVP piramide");
            return;
        }
        double newProfit = state.getBankedProfit() - state.getStartStake();
        localTicketStore.saveMvpPyramid(new MvpPyramidState(
                false,
                0,
                0.0,
                0.0,
                newProfit,
                state.getLastMatch(),
                state.getLastSelection()
        ));
        render();
        setSyncStatus("MVP pao - piramida je resetovana");
    }

    private void cashOutMvpPyramid() {
        MvpPyramidState state = localTicketStore.loadMvpPyramid();
        if (!state.isActive()) {
            setSyncStatus("Nema aktivne MVP piramide");
            return;
        }
        double profit = state.getCurrentStake() - state.getStartStake();
        localTicketStore.saveMvpPyramid(new MvpPyramidState(
                false,
                0,
                0.0,
                0.0,
                state.getBankedProfit() + profit,
                state.getLastMatch(),
                state.getLastSelection()
        ));
        render();
        setSyncStatus("MVP piramida zakljucana");
    }

    private void addValueScreen(LinearLayout parent) {
        addScreenTitle(parent, "VALUE", R.color.accent);
        addSyncStatus(parent);
        parent.addView(sectionLabel("VALUE BETOVI", R.color.accent), topBlockParams(18));
        if (valueBets.isEmpty()) {
            parent.addView(emptyCard("Prazno", "Ovo polje ostaje spremno za value betove."), topBlockParams(10));
            return;
        }
        for (ValueBet valueBet : valueBets) {
            parent.addView(emptyCard(valueBet.getMatch(), valueBet.getSelection()), topBlockParams(10));
        }
    }

    private void addArbScreen(LinearLayout parent) {
        addScreenTitle(parent, "ARB", R.color.blue);
        addSyncStatus(parent);

        parent.addView(sectionLabel("ARBITRAZE", R.color.blue), topBlockParams(18));
        if (arbitrageOpportunities.isEmpty()) {
            parent.addView(emptyCard("Nema arbitraza.", "Pokreni arb scanner na PC-u."), topBlockParams(10));
        } else {
            for (ArbitrageOpportunity opportunity : arbitrageOpportunities) {
                parent.addView(arbCard(opportunity), topBlockParams(14));
            }
        }

        addArbHistory(parent);
    }

    private void addArbHistory(LinearLayout parent) {
        parent.addView(sectionLabel("ISTORIJA ARB", R.color.blue), topBlockParams(28));
        List<List<ArbitrageOpportunity>> history = localTicketStore.loadArbHistory();
        if (history.isEmpty()) {
            parent.addView(emptyCard("Nema istorije", "ARB prilike ce se pamtiti posle prvog uspesnog preuzimanja."), topBlockParams(10));
            return;
        }
        int shown = 0;
        for (List<ArbitrageOpportunity> snapshot : history) {
            if (snapshot.isEmpty()) {
                continue;
            }
            ArbitrageOpportunity best = snapshot.get(0);
            parent.addView(summaryCard(historyTitle(snapshot, "ARB"), best.getMatch() + " - " + best.getProfitLabel()), topBlockParams(10));
            shown++;
            if (shown == 5) {
                break;
            }
        }
    }

    private LinearLayout arbCard(final ArbitrageOpportunity opportunity) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.addView(text(opportunity.getMatch(), 19, R.color.ink, Typeface.BOLD));
        title.addView(text(opportunity.getLeague(), 13, R.color.muted, Typeface.NORMAL));
        head.addView(title, weightedParams());

        LinearLayout profitBox = new LinearLayout(this);
        profitBox.setOrientation(LinearLayout.VERTICAL);
        profitBox.setGravity(Gravity.RIGHT);
        profitBox.addView(text(stripPlus(opportunity.getProfitLabel()), 18, R.color.blue, Typeface.BOLD));
        profitBox.addView(text("PROFIT", 11, R.color.muted, Typeface.BOLD));
        head.addView(profitBox);
        card.addView(head);

        addDivider(card, 16, 14);

        LinearLayout outcomes = new LinearLayout(this);
        outcomes.setOrientation(LinearLayout.HORIZONTAL);
        List<ArbitrageOutcome> shown = firstTwoOutcomes(opportunity);
        for (int i = 0; i < shown.size(); i++) {
            outcomes.addView(arbOutcomeBlock(shown.get(i)), weightedParams());
            if (i == 0 && shown.size() > 1) {
                LinearLayout vertical = new LinearLayout(this);
                vertical.setBackgroundColor(color(R.color.line));
                outcomes.addView(vertical, new LinearLayout.LayoutParams(dp(1), dp(58)));
            }
        }
        card.addView(outcomes);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, dp(16), 0, 0);
        foot.addView(text("Ulog podeljen", 13, R.color.muted, Typeface.NORMAL), weightedParams());
        TextView safe = text("100% sigurna arbitraza", 13, R.color.blue, Typeface.NORMAL);
        safe.setGravity(Gravity.RIGHT);
        foot.addView(safe);
        card.addView(foot);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showArbDetail(opportunity);
            }
        });
        return card;
    }

    private LinearLayout arbOutcomeBlock(ArbitrageOutcome outcome) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(8), 0, dp(8), 0);
        block.addView(bookmakerLine(outcome.getBookmaker(), 14));
        block.addView(text(outcome.getSelection(), 13, R.color.ink, Typeface.NORMAL));
        TextView odds = text(String.format(Locale.US, "%.2f", outcome.getOdds()), 13, R.color.blue, Typeface.NORMAL);
        odds.setGravity(Gravity.RIGHT);
        block.addView(odds);
        return block;
    }

    private void showAiPickDetail(AiPick pick) {
        content.removeAllViews();
        addBackTitle("AI PICK", R.color.accent);
        content.addView(aiPickCard(pick.isMvp() ? "MVP PICK" : "AI PICK", pick, pick.isMvp()), topBlockParams(18));
        content.addView(sectionLabel("ZASTO SE IGRA", R.color.accent), topBlockParams(22));
        content.addView(emptyCard("Analiza", pick.getReason().isEmpty() ? "Nema opisa za ovaj tip." : pick.getReason()), topBlockParams(10));
        List<String> cleanFactors = displayFactors(pick);
        if (!cleanFactors.isEmpty()) {
            StringBuilder factors = new StringBuilder();
            for (String factor : cleanFactors) {
                if (factors.length() > 0) {
                    factors.append("\n");
                }
                factors.append("- ").append(factor);
            }
            content.addView(emptyCard("Faktori", factors.toString()), topBlockParams(10));
        }
        content.addView(summaryCard("MVP racunica", mvpProfitSummary(pick)), topBlockParams(10));
    }

    private void showArbDetail(ArbitrageOpportunity opportunity) {
        content.removeAllViews();
        addBackTitle("ARB", R.color.blue);
        content.addView(arbCard(opportunity), topBlockParams(18));
        content.addView(sectionLabel("RACUNICA", R.color.blue), topBlockParams(22));
        content.addView(emptyCard("Profit", "Belezi se profit u procentima: " + opportunity.getProfitLabel()), topBlockParams(10));
        content.addView(arbCalculatorCard(opportunity), topBlockParams(12));
        for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
            content.addView(emptyCard(displayBookmaker(outcome.getBookmaker()), outcome.getSelection() + " @ " + String.format(Locale.US, "%.2f", outcome.getOdds())), topBlockParams(10));
        }
    }

    private LinearLayout arbCalculatorCard(final ArbitrageOpportunity opportunity) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        card.addView(text("KALKULATOR ULOGA", 14, R.color.blue, Typeface.BOLD));

        final EditText stakeInput = new EditText(this);
        stakeInput.setSingleLine(true);
        stakeInput.setText("1000");
        stakeInput.setTextSize(18);
        stakeInput.setTextColor(color(R.color.ink));
        stakeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        stakeInput.setSelectAllOnFocus(true);
        stakeInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        stakeInput.setHint("Ukupan ulog");
        LinearLayout.LayoutParams inputParams = blockParams();
        inputParams.topMargin = dp(12);
        card.addView(stakeInput, inputParams);

        final LinearLayout resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);

        TextView button = text("IZRACUNAJ", 14, android.R.color.white, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.blue_button_bg);
        LinearLayout.LayoutParams buttonParams = blockParams();
        buttonParams.topMargin = dp(12);
        card.addView(button, buttonParams);

        LinearLayout.LayoutParams resultParams = blockParams();
        resultParams.topMargin = dp(14);
        card.addView(resultBox, resultParams);

        updateArbCalculatorResults(resultBox, opportunity, 1000.0);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateArbCalculatorResults(resultBox, opportunity, parseMoney(stakeInput.getText().toString(), 1000.0));
            }
        });

        return card;
    }

    private void updateArbCalculatorResults(LinearLayout resultBox, ArbitrageOpportunity opportunity, double totalStake) {
        resultBox.removeAllViews();
        if (totalStake <= 0.0 || opportunity.getOutcomes().isEmpty()) {
            resultBox.addView(text("Unesi ukupan ulog veci od 0.", 13, R.color.muted, Typeface.NORMAL));
            return;
        }

        double impliedSum = 0.0;
        for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
            if (outcome.getOdds() > 1.0) {
                impliedSum += 1.0 / outcome.getOdds();
            }
        }
        if (impliedSum <= 0.0) {
            resultBox.addView(text("Kvote nisu validne za kalkulator.", 13, R.color.muted, Typeface.NORMAL));
            return;
        }

        double guaranteedPayout = Double.MAX_VALUE;
        for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
            double stake = (totalStake / outcome.getOdds()) / impliedSum;
            double payout = stake * outcome.getOdds();
            guaranteedPayout = Math.min(guaranteedPayout, payout);
            resultBox.addView(calculatorRow(displayBookmaker(outcome.getBookmaker()), outcome.getSelection(), stake, outcome.getOdds()));
        }

        double profit = guaranteedPayout - totalStake;
        double profitPct = (profit / totalStake) * 100.0;
        addDivider(resultBox, 12, 10);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(text("Garantovani profit", 13, R.color.ink, Typeface.BOLD), weightedParams());
        TextView value = text(String.format(Locale.US, "+%.2f (%.2f%%)", profit, profitPct), 15, R.color.blue, Typeface.BOLD);
        value.setGravity(Gravity.RIGHT);
        footer.addView(value);
        resultBox.addView(footer);
    }

    private LinearLayout calculatorRow(String bookmaker, String selection, double stake, double odds) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(bookmakerLine(bookmaker, 14));
        left.addView(text(selection + " @ " + String.format(Locale.US, "%.2f", odds), 12, R.color.muted, Typeface.NORMAL));
        row.addView(left, weightedParams());

        TextView amount = text(String.format(Locale.US, "%.2f", stake), 15, R.color.blue, Typeface.BOLD);
        amount.setGravity(Gravity.RIGHT);
        row.addView(amount, new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private double parseMoney(String value, double fallback) {
        try {
            return Double.parseDouble(value.replace(",", ".").trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addBackTitle(String title, int accentColor) {
        TextView back = text("< Nazad", 15, accentColor, Typeface.BOLD);
        back.setPadding(0, 0, 0, dp(18));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                render();
            }
        });
        content.addView(back, blockParams());
        addScreenTitle(content, title, accentColor);
    }

    private void addSyncStatus(LinearLayout parent) {
        syncStatus = text("", 12, R.color.muted, Typeface.NORMAL);
        syncStatus.setGravity(Gravity.CENTER);
        syncStatus.setPadding(0, 0, 0, dp(14));
        parent.addView(syncStatus, blockParams());
    }

    private TextView actionButton(String label, int backgroundColor, View.OnClickListener listener) {
        TextView button = text(label, 14, android.R.color.white, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(11), dp(12), dp(11));
        if (backgroundColor == R.color.blue) {
            button.setBackgroundResource(R.drawable.blue_button_bg);
        } else if (backgroundColor == R.color.orange) {
            button.setBackgroundResource(R.drawable.orange_button_bg);
        } else {
            button.setBackgroundResource(R.drawable.green_button_bg);
        }
        button.setOnClickListener(listener);
        return button;
    }

    private void showAiDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker view, int year, int month, int dayOfMonth) {
                        selectedAiCalendar.set(Calendar.YEAR, year);
                        selectedAiCalendar.set(Calendar.MONTH, month);
                        selectedAiCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        render();
                    }
                },
                selectedAiCalendar.get(Calendar.YEAR),
                selectedAiCalendar.get(Calendar.MONTH),
                selectedAiCalendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private String selectedAiDate() {
        return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                selectedAiCalendar.get(Calendar.YEAR),
                selectedAiCalendar.get(Calendar.MONTH) + 1,
                selectedAiCalendar.get(Calendar.DAY_OF_MONTH)
        );
    }

    private LinearLayout summaryCard(String title, String bodyText) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text(title, 14, R.color.ink, Typeface.BOLD));
        TextView body = text(bodyText, 13, R.color.muted, Typeface.NORMAL);
        body.setPadding(0, dp(6), 0, 0);
        card.addView(body);
        return card;
    }

    private String parlaySummary(List<AiPick> picks) {
        double combined = 1.0;
        int count = 0;
        for (AiPick pick : picks) {
            if (pick.getOdds() > 1.0) {
                combined *= pick.getOdds();
                count++;
            }
            if (count == 3) {
                break;
            }
        }
        if (count == 0) {
            return "Parlay kvota nije dostupna.";
        }
        return String.format(Locale.US, "Sva 3 picka se racunaju kao jedan parlay. Kombinovana kvota %.2f. Ulog 1%% bankrolla, potencijalni profit +%.0f%% uloga ako sva tri prodju. MVP se pored toga racuna kao poseban single.", combined, (combined - 1.0) * 100.0);
    }

    private String mvpProfitSummary(AiPick pick) {
        if (pick.getOdds() <= 1.0) {
            return "Kvota nije dostupna.";
        }
        return String.format(Locale.US, "Ovaj pick je deo parlay-a, ali se dodatno racuna kao poseban single. Ulog 1%% bankrolla, potencijalni profit +%.0f%% uloga.", (pick.getOdds() - 1.0) * 100.0);
    }

    private String ticketHistoryTitle(AiTicketRecord record) {
        String status = "WIN".equals(record.getStatus()) ? "PROSAO" : "PAO";
        return record.getType() + " - " + status + " - " + record.getCreatedAt();
    }

    private String ticketHistoryBody(AiTicketRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append("Kvota: ").append(record.getOddsLabel());
        double profit = "WIN".equals(record.getStatus()) ? Math.max(0.0, record.getOdds() - 1.0) * 100.0 : -100.0;
        builder.append(" | Rezultat: ").append("WIN".equals(record.getStatus()) ? "WIN" : "LOSS");
        builder.append(" | Profit: ").append(String.format(Locale.US, "%+.0f%% uloga", profit));
        for (AiPick pick : record.getPicks()) {
            builder.append("\n").append(pick.getMatch()).append(" - ").append(pick.getSelection()).append(" @ ").append(pick.getOddsLabel());
        }
        if ("PARLAY".equals(record.getType())) {
            builder.append("\nParlay se racuna kao celina: ako padne jedan pick, pao je ceo tiket.");
        }
        return builder.toString();
    }

    private String historyTitle(List<?> snapshot, String type) {
        return type + " zapis - " + snapshot.size() + " stavki";
    }

    private void addScreenTitle(LinearLayout parent, String title, int accentColor) {
        TextView titleView = text(title, 24, R.color.ink, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        parent.addView(titleView, blockParams());

        TextView line = text("________", 18, accentColor, Typeface.BOLD);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, 0, 0, dp(22));
        parent.addView(line, blockParams());
    }

    private TextView sectionLabel(String value, int colorId) {
        TextView label = text(value, 16, colorId, Typeface.BOLD);
        label.setPadding(0, 0, 0, dp(2));
        return label;
    }

    private LinearLayout emptyCard(String title, String subtitle) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.addView(text(title, 17, R.color.ink, Typeface.BOLD));
        TextView body = text(subtitle, 13, R.color.muted, Typeface.NORMAL);
        body.setPadding(0, dp(6), 0, 0);
        card.addView(body);
        return card;
    }

    private LinearLayout bottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(8));
        nav.setMinimumHeight(dp(82));
        nav.setBackgroundColor(color(R.color.panel));
        nav.addView(navItem(R.drawable.ai_picks_icon_nav, "AI", "ai"), weightedParams());
        nav.addView(navItem(R.drawable.pyramid_icon_nav, "PYRAMID", "pyramid"), weightedParams());
        nav.addView(navItem(R.drawable.arb_icon_nav, "ARB", "arb"), weightedParams());
        return nav;
    }

    private LinearLayout navItem(int iconRes, String label, final String targetScreen) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(1), 0, dp(1), 0);
        boolean active = screen.equals(targetScreen);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setAdjustViewBounds(true);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setAlpha(active ? 1.0f : 0.45f);
        item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(38)));

        TextView labelView = text(label, 10, active ? R.color.ink : R.color.muted, Typeface.BOLD);
        labelView.setGravity(Gravity.CENTER);
        labelView.setAlpha(active ? 1.0f : 0.65f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dp(2);
        item.addView(labelView, labelParams);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                screen = targetScreen;
                render();
            }
        });
        return item;
    }

    private List<String> displayFactors(AiPick pick) {
        List<String> clean = new ArrayList<>();
        String reason = normalizeText(pick.getReason());
        for (String factor : pick.getKeyFactors()) {
            String value = factor.replace("- ", "").trim();
            String normalized = normalizeText(value);
            if (value.length() < 8 || normalized.isEmpty()) {
                continue;
            }
            if (!reason.isEmpty() && (reason.equals(normalized) || reason.contains(normalized) || normalized.contains(reason))) {
                continue;
            }
            boolean duplicate = false;
            for (String existing : clean) {
                String existingNormalized = normalizeText(existing);
                if (existingNormalized.equals(normalized)
                        || existingNormalized.contains(normalized)
                        || normalized.contains(existingNormalized)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                clean.add(value);
            }
            if (clean.size() == 3) {
                break;
            }
        }
        return clean;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private void scheduleMidnightRollover() {
        if (midnightRollover != null) {
            dayHandler.removeCallbacks(midnightRollover);
        }
        midnightRollover = new Runnable() {
            @Override
            public void run() {
                localTicketStore.rolloverAiLatestIfOld();
                aiPicks = localTicketStore.loadAiLatest();
                if ("ai".equals(screen)) {
                    render();
                    setSyncStatus("Novi dan - AI prostor je spreman za nove tipove");
                }
                scheduleMidnightRollover();
            }
        };
        dayHandler.postDelayed(midnightRollover, delayUntilNextMidnight());
    }

    private long delayUntilNextMidnight() {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_YEAR, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        return Math.max(1000L, next.getTimeInMillis() - now.getTimeInMillis());
    }

    private AiPick findMvpPick() {
        for (AiPick pick : aiPicks) {
            if (pick.isMvp()) {
                return pick;
            }
        }
        return aiPicks.isEmpty() ? null : aiPicks.get(0);
    }

    private List<AiPick> firstThreePicks() {
        List<AiPick> picks = new ArrayList<>();
        for (AiPick pick : aiPicks) {
            picks.add(pick);
            if (picks.size() == 3) {
                break;
            }
        }
        return picks;
    }

    private List<AiPick> regularAiPicks() {
        List<AiPick> regular = new ArrayList<>();
        for (AiPick pick : aiPicks) {
            if (!pick.isMvp()) {
                regular.add(pick);
            }
        }
        if (regular.isEmpty()) {
            return aiPicks;
        }
        return regular;
    }

    private String metaLine(AiPick pick) {
        String league = pick.getLeague().isEmpty() ? "Football" : pick.getLeague();
        String time = pick.getKickoff().isEmpty() ? "vreme nije dostupno" : pick.getKickoff();
        return league + "  -  " + time;
    }

    private List<ArbitrageOutcome> firstTwoOutcomes(ArbitrageOpportunity opportunity) {
        List<ArbitrageOutcome> outcomes = new ArrayList<>();
        for (ArbitrageOutcome outcome : opportunity.getOutcomes()) {
            outcomes.add(outcome);
            if (outcomes.size() == 2) {
                break;
            }
        }
        return outcomes;
    }

    private String stripPlus(String value) {
        return value.startsWith("+") ? value.substring(1) : value;
    }

    private String displayBookmaker(String rawBookmaker) {
        String raw = rawBookmaker == null ? "" : rawBookmaker.trim();
        if (raw.isEmpty()) {
            return "Kladionica nije dostupna";
        }
        String lower = raw.toLowerCase(Locale.US).replace(" ", "").replace("-", "");
        if (lower.equals("booka") || lower.equals("bookb") || lower.equals("bookiea") || lower.equals("bookieb")) {
            return "Kladionica nije dostupna";
        }
        if (lower.equals("1xbet")) {
            return "1xBet";
        }
        if (lower.equals("superbet")) {
            return "Superbet";
        }
        if (lower.equals("maxbet")) {
            return "MaxBet";
        }
        if (lower.equals("merkurxtip") || lower.equals("xtip")) {
            return "MerkurXtip";
        }
        if (lower.equals("meridianbet") || lower.equals("meridian")) {
            return "Meridianbet";
        }
        if (lower.equals("mozzart") || lower.equals("mozzartbet")) {
            return "Mozzart";
        }
        if (lower.equals("bet365")) {
            return "bet365";
        }
        if (lower.equals("admiralbetde")) {
            return "AdmiralBet";
        }
        return raw;
    }

    private LinearLayout bookmakerLine(String rawBookmaker, int textSize) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        int logo = bookmakerLogo(rawBookmaker);
        if (logo != 0) {
            ImageView logoView = new ImageView(this);
            logoView.setImageResource(logo);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoView.setContentDescription(displayBookmaker(rawBookmaker));
            line.addView(logoView, new LinearLayout.LayoutParams(dp(34), dp(24)));
        }

        TextView name = text(displayBookmaker(rawBookmaker), textSize, R.color.ink, Typeface.BOLD);
        if (logo != 0) {
            name.setPadding(dp(5), 0, 0, 0);
        }
        line.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return line;
    }

    private int bookmakerLogo(String rawBookmaker) {
        String normalized = displayBookmaker(rawBookmaker).toLowerCase(Locale.US).replace(" ", "");
        if (normalized.equals("1xbet")) {
            return R.drawable.xbet_logo;
        }
        if (normalized.equals("superbet")) {
            return R.drawable.superbet_logo;
        }
        return 0;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setBackgroundResource(R.drawable.card_bg);
        return card;
    }

    private void addDivider(LinearLayout parent, int top, int bottom) {
        LinearLayout divider = new LinearLayout(this);
        divider.setBackgroundColor(color(R.color.line));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        dividerParams.setMargins(0, dp(top), 0, dp(bottom));
        parent.addView(divider, dividerParams);
    }

    private LinearLayout.LayoutParams blockParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams topBlockParams(int topMargin) {
        LinearLayout.LayoutParams params = blockParams();
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView text(String value, int sp, int colorId, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(colorId));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private int color(int id) {
        return getResources().getColor(id);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setSyncStatus(String value) {
        if (syncStatus != null) {
            syncStatus.setText(value);
        }
    }

    private String shortError(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "generisanje nije uspelo";
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        String lower = message.toLowerCase();
        if (lower.contains("failed to connect")
                || lower.contains("connection refused")
                || lower.contains("connect timed out")
                || lower.contains("no route to host")) {
            return "backend nije dostupan. Pokreni start_surebet.bat, proveri da su telefon i PC na istom Wi-Fi i dozvoli firewall";
        }
        return message.length() > 120 ? message.substring(0, 117) + "..." : message;
    }
}

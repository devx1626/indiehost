#!/usr/bin/env python3
"""Readout for a two-variant A/B test. Standard library only.

Conversions (users and conversions per variant):
  python3 readout.py --control 48210 5302 --treatment 48655 5598 --mde 0.05

Continuous metric (users, mean and standard deviation per variant):
  python3 readout.py --control-mean 48210 41.2 38.5 --treatment-mean 48655 42.0 39.1

Options:
  --split 0.5   planned share of users in control, for the sample ratio check
  --alpha 0.05  two-sided significance level
  --mde 0.05    minimum detectable effect, relative; adds a verdict against it

Uses normal approximations, which hold at the sample sizes of product experiments
(hundreds of conversions per variant). For metrics whose unit differs from the
randomization unit (revenue per order, clicks per session), use the delta method
or your experimentation platform instead.
"""
import argparse
import math
from statistics import NormalDist

NORMAL = NormalDist()
SRM_THRESHOLD = 0.001


def srm_check(n_control, n_treatment, split=0.5):
    """Chi-square goodness-of-fit test of the observed split. Returns (chi2, p-value)."""
    total = n_control + n_treatment
    expected_control, expected_treatment = total * split, total * (1 - split)
    chi2 = ((n_control - expected_control) ** 2 / expected_control
            + (n_treatment - expected_treatment) ** 2 / expected_treatment)
    return chi2, math.erfc(math.sqrt(chi2 / 2))  # survival function, 1 degree of freedom


def compare_rates(n_c, x_c, n_t, x_t, alpha=0.05):
    p_c, p_t = x_c / n_c, x_t / n_t
    z_crit = NORMAL.inv_cdf(1 - alpha / 2)
    diff = p_t - p_c
    se = math.sqrt(p_c * (1 - p_c) / n_c + p_t * (1 - p_t) / n_t)
    pooled = (x_c + x_t) / (n_c + n_t)
    se_pooled = math.sqrt(pooled * (1 - pooled) * (1 / n_c + 1 / n_t))
    p_value = 2 * NORMAL.cdf(-abs(diff / se_pooled)) if se_pooled else 1.0
    if x_c and x_t:  # relative lift interval from the log risk ratio (Katz)
        ratio = p_t / p_c
        se_log = math.sqrt(1 / x_t - 1 / n_t + 1 / x_c - 1 / n_c)
        lift, lift_ci = ratio - 1, (ratio * math.exp(-z_crit * se_log) - 1, ratio * math.exp(z_crit * se_log) - 1)
    else:
        lift, lift_ci = float("nan"), (float("nan"), float("nan"))
    return {"control": p_c, "treatment": p_t, "diff": diff,
            "diff_ci": (diff - z_crit * se, diff + z_crit * se),
            "lift": lift, "lift_ci": lift_ci, "p_value": p_value}


def compare_means(n_c, m_c, s_c, n_t, m_t, s_t, alpha=0.05):
    z_crit = NORMAL.inv_cdf(1 - alpha / 2)
    diff = m_t - m_c
    var_c, var_t = s_c ** 2 / n_c, s_t ** 2 / n_t
    se = math.sqrt(var_c + var_t)
    p_value = 2 * NORMAL.cdf(-abs(diff / se)) if se else 1.0
    lift = m_t / m_c - 1
    se_lift = math.sqrt(var_t / m_c ** 2 + m_t ** 2 * var_c / m_c ** 4)  # delta method
    return {"control": m_c, "treatment": m_t, "diff": diff,
            "diff_ci": (diff - z_crit * se, diff + z_crit * se),
            "lift": lift, "lift_ci": (lift - z_crit * se_lift, lift + z_crit * se_lift),
            "p_value": p_value}


def verdict(lift_ci, mde=None):
    low, high = lift_ci
    if low > 0:
        if mde is None or low >= mde:
            return "Clear win: the whole interval is above " + ("the MDE." if mde else "zero.")
        return "Positive and significant, but lifts smaller than the MDE are still plausible: judgment call."
    if high < 0:
        return "The change hurts: the whole interval is below zero."
    if mde is not None and -mde < low and high < mde:
        return "Flat: no effect as large as the MDE. A useful result."
    if mde is not None:
        return "Inconclusive: the interval includes zero and effects larger than the MDE. Too little data to tell."
    return "Not significant: the interval includes zero."


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    data = parser.add_mutually_exclusive_group(required=True)
    data.add_argument("--control", nargs=2, type=float, metavar=("USERS", "CONVERSIONS"))
    data.add_argument("--control-mean", nargs=3, type=float, metavar=("USERS", "MEAN", "STD"))
    parser.add_argument("--treatment", nargs=2, type=float, metavar=("USERS", "CONVERSIONS"))
    parser.add_argument("--treatment-mean", nargs=3, type=float, metavar=("USERS", "MEAN", "STD"))
    parser.add_argument("--split", type=float, default=0.5, help="planned share of users in control (default 0.5)")
    parser.add_argument("--alpha", type=float, default=0.05, help="two-sided significance level (default 0.05)")
    parser.add_argument("--mde", type=float, help="minimum detectable effect, relative (0.05 = +5%%)")
    args = parser.parse_args(argv)

    rates = args.control is not None
    if rates and args.treatment is None:
        parser.error("--control needs --treatment")
    if not rates and args.treatment_mean is None:
        parser.error("--control-mean needs --treatment-mean")

    control = args.control if rates else args.control_mean
    treatment = args.treatment if rates else args.treatment_mean
    n_c, n_t = int(control[0]), int(treatment[0])
    level = f"{1 - args.alpha:.0%}"

    chi2, p_srm = srm_check(n_c, n_t, args.split)
    srm_failed = p_srm < SRM_THRESHOLD
    print(f"Sample ratio check: control {n_c:,} / treatment {n_t:,} "
          f"(planned {args.split:.0%}/{1 - args.split:.0%}), chi2 = {chi2:.2f}, p = {p_srm:.3g} -> "
          + ("MISMATCH" if srm_failed else "OK"))
    if srm_failed:
        print("  Assignment or exposure logging is broken. Find the cause before reading the results below.")
    print()

    if rates:
        result = compare_rates(n_c, int(control[1]), n_t, int(treatment[1]), args.alpha)
        print(f"{'':<10} {'users':>10} {'conversions':>12} {'rate':>8}")
        print(f"{'control':<10} {n_c:>10,} {int(control[1]):>12,} {result['control']:>8.2%}")
        print(f"{'treatment':<10} {n_t:>10,} {int(treatment[1]):>12,} {result['treatment']:>8.2%}")
        low, high = result["diff_ci"]
        print(f"\nDifference:    {result['diff'] * 100:+.2f} pp ({level} CI {low * 100:+.2f} to {high * 100:+.2f} pp)")
    else:
        result = compare_means(n_c, control[1], control[2], n_t, treatment[1], treatment[2], args.alpha)
        print(f"{'':<10} {'users':>10} {'mean':>10} {'std':>10}")
        print(f"{'control':<10} {n_c:>10,} {control[1]:>10.4g} {control[2]:>10.4g}")
        print(f"{'treatment':<10} {n_t:>10,} {treatment[1]:>10.4g} {treatment[2]:>10.4g}")
        low, high = result["diff_ci"]
        print(f"\nDifference:    {result['diff']:+.4g} ({level} CI {low:+.4g} to {high:+.4g})")
    low, high = result["lift_ci"]
    print(f"Relative lift: {result['lift']:+.2%} ({level} CI {low:+.2%} to {high:+.2%})")
    print(f"p-value:       {result['p_value']:.3g}")
    label = f" (MDE {args.mde:+.1%})" if args.mde is not None else ""
    if srm_failed:
        print(f"Verdict{label}: Invalid: sample ratio mismatch.")
    else:
        print(f"Verdict{label}: {verdict(result['lift_ci'], args.mde)}")


if __name__ == "__main__":
    main()

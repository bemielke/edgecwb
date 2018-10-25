/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 1999-2000 Anthony Lomax <anthony@alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.alomax.timedom;

import net.alomax.text.LocalizedText;

/** A class for multi-language text
 *
 * All of the methods of this class are static.
 */
public class TimeDomainText extends LocalizedText {

    // default en_US text
    // general time domain exceptions
    public static String invalid_time_domain_process = "invalid time domain process";
    public static String does_not_support_copy = "time domain process does not support copy (not valid for recursive operation)";
    public static String does_not_support_memory = "time domain process does not support memory (not valid for recursive operation)";
    // function specific exceptions
    public static String invalid_alpha_value = "invalid alpha value";
    public static String invalid_window_width_value = "invalid window width value";
    public static String invalid_result_value = "invalid result value";
    public static String invalid_short_term_window_value = "invalid sta/lta short term window value";
    public static String invalid_long_term_window_value = "invalid sta/lta long term window value";
    public static String invalid_ratio_value = "invalid sta/lta ratio value";
    public static String invalid_direction_value = "invalid picker direction value";
    public static String invalid_smoothing_half_width_value = "invalid smoothing half width value";
    public static String invalid_smoothing_type = "invalid smoothing type";
    public static String invalid_moments_width_value = "invalid moments width value";
    public static String invalid_moments_type = "invalid moments type";
    public static String invalid_peakext_max_num_peaks_value = "invalid maximum number peaks value";
    public static String invalid_min_amplitude_value = "invalid minimum amplitude value (not 0-1)";
    public static String invalid_deviation_value = "invalid deviation value (not > 0)";
    public static String invalid_threshold1_value = "invalid picker threshold1 value";
    public static String invalid_threshold2_value = "invalid picker threshold2 value";
    public static String invalid_tUpEvent_value = "invalid picker tUpEvent value";
    public static String invalid_tUpEventMin_value = "invalid picker tUpEventMin value";
    public static String tUpEventMin_ge_tUpEvent = "tUpEventMin value must be < tUpEvent value";
    public static String invalid_meanWindow_value = "invalid picker meanWindow value";
    public static String invalid_filterWindow_value = "invalid picker filterWindow value";
    public static String invalid_tDownMax_value = "invalid picker tDownMax value";
    public static String invalid_cumdur_threshold_value = "invalid cumulative duration threshold value (not 0-1)";
    public static String invalid_cumdur_cutoff_value = "invalid cumulative duration cutoff value (not 0-1)";
    public static String invalid_cumdur_duration_fraction_value = "invalid cumulative duration threshold value";
    public static String invalid_cumdur_duration_min_value = "invalid cumulative duration minimum value";
    public static String invalid_coeff_value = "invalid coeff value";
    public static String invalid_power_value = "invalid power value";
    public static String invalid_mwpdConst_value = "invalid Mwpd constant value";
    public static String invalid_function_generator_type = "invalid function generator type";
    public static String invalid_function_generator_value = "invalid function generator type";
    public static String invalid_recursion_filter_type = "invalid recursion filter type";
    public static String recursion_filter_number_coefficients = "sample length less than number of recursion filter coefficients";
    public static String invalid_peakwin_width_value = "invalid peak window width value";
    public static String invalid_peakwin_measurement_step_value = "invalid peak window measurement step value";
    public static String invalid_peakwin_threshold_value = "invalid peak window threshold value";
    public static String invalid_amplitude_at_feature_offset_value = "invalid amplitude at feature offset value";
    // new 201502
    public static String data_sample_interval_changed = "data sample interval (dt) changed";
    public static String filter_period_less_than_sampling_interval = "filter period less than sampling interval";
    public static String filter_parameters_not_set = "filter parameters not set";
    public static String invalid_period_value = "invalid period value";
    public static String invalid_threshold1_scale_factor_value = "invalid threshold1 scale factor value";
    public static String invalid_threshold1_value_must_be_ge_threshold2 = "invalid picker threshold1 value, must be >= threshold2";
    public static String invalid_number_of_poles_value = "invalid number of poles value, must be divisible by 4";
    public static String invalid_band_width_value_value = "invalid band width value";
    public static String invalid_band_parameters = "invalid band parameters";


    /** Sets locale and text */
    public static void setLocale(String locName) {

        LocalizedText.setLocale(locName);

        // French, France

        if (localeName.toLowerCase().startsWith("fr")) {
        } // Italian, Italy
        else if (localeName.toLowerCase().startsWith("it")) {
        } // Portugues, Brasil
        else if (localeName.toLowerCase().startsWith("pt")) {

            // do not change the following names, they are used for command line parsing logic
	    /*NAME_IMPULSE = "Pulso";
	    NAME_SINE = "Sen"+o_a+"ide";
	    NAME_GAUSSIAN_NOISE = "Ru"+i_a+"do Gaussiano";
	    NAME_EXP_DECAY = "Decaimento Exponencial";*/

            // general time domain exceptions
            invalid_time_domain_process = "Processo Inv" + a_a + "lido no Dom" + i_a + "nioDoTempo";
            does_not_support_copy = "o processo nao suporta c" + o_a + "pia (exceto em casos de operacoes recursivas)";
            does_not_support_memory = "o processo nao suporta mem" + o_a + "ria";


            // function specific exceptions

            invalid_alpha_value = "valor de Alpha inv" + a_a + "lido";

            invalid_window_width_value = "valor da Largura da Janela inv" + a_a + "lido";

            invalid_result_value = "resultado inv" + a_a + "lido";

            invalid_short_term_window_value = "janela STA inv" + a_a + "lida";
            invalid_long_term_window_value = "janela LTA inv" + a_a + "lida";
            invalid_ratio_value = "raz" + a_t + "o LTA/STA invalida";
            invalid_direction_value = "dire" + c_5 + "" + a_t + "o da pickagem inv" + a_a + "lida";

            invalid_smoothing_half_width_value = "valor de meia-suaviza" + c_5 + "" + a_t + "o inv" + a_a + "lido";
            invalid_smoothing_type = "valor de suaviza" + c_5 + "" + a_t + "o inv" + a_a + "lido";

            invalid_moments_width_value = "valor de momento inv" + a_a + "lido";
            invalid_moments_type = "tipo de momento inv" + a_a + "lido";

            invalid_peakext_max_num_peaks_value = "m" + a_a + "ximo numero de pickagens inv" + a_a + "lido";
            invalid_min_amplitude_value = "valor m" + i_a + "nimo de amplitude inv" + a_a + "ludo (nao 0-1)";
            invalid_deviation_value = "valor de desvio inv" + a_a + "lido (nao > 0)";

            invalid_threshold1_value = "limiar1 inv" + a_a + "lido";
            invalid_threshold2_value = "limiar2 inv" + a_a + "lido";
            invalid_tUpEvent_value = "pickagem tUpEvent inv" + a_a + "lida";
            invalid_meanWindow_value = "valor da janelaM" + e_a + "dia inv" + a_a + "lido";
            invalid_filterWindow_value = "valor da janela do filtro inv" + a_a + "lida";
            invalid_tDownMax_value = "valor tDownMax inv" + a_a + "lido";

            invalid_cumdur_threshold_value = "valor de limiar de dura" + c_5 + "" + a_t + "o cumulativa inv" + a_a + "lido (nao 0-1)";
            invalid_cumdur_cutoff_value = "valor de corte de duracao cumulativa inv" + a_a + "lido (nao 0-1)";
            invalid_cumdur_duration_fraction_value = "valor de fracao de duracao invalido";
            invalid_cumdur_duration_min_value = "valor m" + i_a + "nimo inv" + a_a + "lido";

            invalid_coeff_value = "valor do coeficiente inv" + a_a + "lido";
            invalid_power_value = "valor da pot" + e_c + "ncia inv" + a_a + "lido";

            invalid_mwpdConst_value = "constante Mwpd inv" + a_a + "lida";

            invalid_function_generator_type = "type da fun" + c_5 + "" + a_t + "o gerador inv" + a_a + "lida";
            invalid_function_generator_value = "valor da fun" + c_5 + "" + a_t + "o gerador inv" + a_a + "lida";

            invalid_recursion_filter_type = "filtro de recursao inv" + a_a + "lido";
            recursion_filter_number_coefficients = "comprimento de amostras menor que o numedo de coeficientes do filtro de recurs" + a_t + "o";

            invalid_peakwin_width_value = "peakwin: largura da janela de pickagem inv" + a_a + "lida";
            invalid_peakwin_measurement_step_value = "peakwin: valor do passo inv" + a_a + "lido";
            invalid_peakwin_threshold_value = "peakwin: limiar inv" + a_a + "lido";

            invalid_amplitude_at_feature_offset_value = "o valor da amplitude nesse ponto " + e_a + " inv" + a_a + "lido";

        } // default English, USA
        else {		// use default en_US
        }

    }
}	// end class FreqText

